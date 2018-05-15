package com.navneetgupta.bookstore.order

import com.navneetgupta.bookstore.common.ErrorMessage
import com.navneetgupta.bookstore.domain.book.Book
import com.navneetgupta.bookstore.domain.order.{ CreateOrder, SalesOrderLineItem }
import com.navneetgupta.bookstore.domain.user.BookStoreUser

import akka.actor.{ ActorRef, FSM, Identify, Props }
import akka.actor.ActorIdentity
import com.navneetgupta.bookstore.domain.user.FindUserById
import com.navneetgupta.bookstore.domain.book.FindBook
import com.navneetgupta.bookstore.common.FullResult
import com.navneetgupta.bookstore.common.Failure
import com.navneetgupta.bookstore.common.FailureType
import com.navneetgupta.bookstore.common.ServiceResult
import com.navneetgupta.bookstore.common.EmptyResult
import java.util.Date
import com.navneetgupta.bookstore.domain.credit.ChargeCreditCard
import com.navneetgupta.bookstore.domain.credit.CreditCardTransaction
import com.navneetgupta.bookstore.domain.credit.CreditTransactionStatus
import com.navneetgupta.bookstore.domain.order.SaleOrder
import com.navneetgupta.bookstore.domain.order.SalesOrderStatus
import akka.actor.Status

object OrderProcessor {

  val UserManagerName = "user-manager"
  val CreditHandlerName = "credit-handler"

  def props = Props[OrderProcessor]

  sealed trait State
  case object Idle extends State
  case object ResolvingDependencies extends State
  case object LookingUpEntities extends State
  case object ChargingCard extends State
  case object WritingEntity extends State

  sealed trait Data { def originator: ActorRef }
  case class Inputs(originator: ActorRef, request: CreateOrder)
  trait InputsData extends Data {
    def inputs: Inputs
    override def originator = inputs.originator
  }
  case object Uninitialized extends Data {
    def originator = ActorRef.noSender
  }
  case class UnResolvedDependencies(
    inputs: Inputs,
    userMgr: Option[ActorRef] = None,
    bookMgr: Option[ActorRef] = None,
    creditHandler: Option[ActorRef] = None) extends InputsData
  case class ResolvedDependencies(
    inputs: Inputs,
    expectedBooks: Set[Int] = Set.empty,
    user: Option[BookStoreUser],
    books: Map[Int, Book] = Map.empty,
    userMgr: ActorRef,
    bookMgr: ActorRef,
    creditHandler: ActorRef) extends InputsData
  case class LookedUpData(inputs: Inputs,
                          user: BookStoreUser,
                          items: List[SalesOrderLineItem],
                          total: Double) extends InputsData

  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))

  object ResolutionIdent extends Enumeration {
    val Book, User, Credit = Value
  }

}
class OrderProcessor extends FSM[OrderProcessor.State, OrderProcessor.Data] {
  import OrderManager._
  import OrderProcessor._
  import context.dispatcher

  val dao = new OrderManagerDao

  startWith(Idle, Uninitialized)
  when(Idle) {
    case Event(req: CreateOrder, _) =>
      lookup(BookMgrName) ! Identify(ResolutionIdent.Book)
      lookup(UserManagerName) ! Identify(ResolutionIdent.User)
      lookup(CreditHandlerName) ! Identify(ResolutionIdent.Credit)

      goto(ResolvingDependencies) using UnResolvedDependencies(Inputs(sender(), req))
  }

  when(ResolvingDependencies, ResolveTimeout)(transform {
    case Event(ActorIdentity(identifier: ResolutionIdent.Value, actor @ Some(ref)), data: UnResolvedDependencies) =>
      val newData = identifier match {
        case ResolutionIdent.Book =>
          data.copy(bookMgr = actor)
        case ResolutionIdent.User =>
          data.copy(userMgr = actor)
        case ResolutionIdent.Credit =>
          data.copy(creditHandler = actor)
      }
      stay using newData
  } using {
    case FSM.State(state, UnResolvedDependencies(inputs, Some(user), Some(book), Some(credit)), _, _, _) =>
      user ! FindUserById(inputs.request.userId)
      val expectedBooks = inputs.request.lineItems.map(_.bookId).toSet
      expectedBooks.foreach(id => book ! FindBook(id))
      goto(LookingUpEntities) using
        ResolvedDependencies(inputs, expectedBooks, None,
          Map.empty, user, book, credit)
  })

  when(LookingUpEntities, ResolveTimeout)(transform {
    case Event(FullResult(b: Book), data: ResolvedDependencies) =>
      val lineItemForBook = data.inputs.request.lineItems.find(_.bookId == b.id)
      lineItemForBook match {
        case None =>
          data.originator ! unexpectedFail
          stop
        case Some(item) if item.quantity > b.inventoryAmount =>
          val invfail = Failure(
            FailureType.Validation, InventoryNotAvailError)
          data.originator ! invfail
          stop
        case _ =>
          stay using data.copy(books = data.books ++ Map(b.id -> b))
      }
    case Event(FullResult(u: BookStoreUser), d: ResolvedDependencies) =>
      stay using d.copy(user = Some(u))

    case Event(EmptyResult, data: ResolvedDependencies) =>
      val (etype, error) =
        if (sender().path.name == BookMgrName)
          ("book", InvalidBookIdError)
        else
          ("user", InvalidUserIdError)
      data.originator ! Failure(FailureType.Validation, error)
      stop
  } using {
    case FSM.State(state, ResolvedDependencies(inputs, expectedBooks, Some(u),
      bookMap, userMgr, bookMgr, creditHandler), _, _, _) if bookMap.keySet == expectedBooks =>
      val lineItems = inputs.request.lineItems.
        flatMap { item =>
          bookMap.
            get(item.bookId).
            map { b =>
              SalesOrderLineItem(0, 0, b.id, item.quantity,
                item.quantity * b.cost, newDate, newDate)
            }
        }
      val total = lineItems.map(_.cost).sum
      creditHandler ! ChargeCreditCard(inputs.request.cardInfo, total)
      goto(ChargingCard) using LookedUpData(
        inputs, u, lineItems, total)
  })

  when(ChargingCard, ResolveTimeout) {
    case Event(FullResult(txn: CreditCardTransaction), data: LookedUpData) if txn.status == CreditTransactionStatus.Approved =>
      import akka.pattern.pipe
      val order = SaleOrder(0, data.user.id, txn.id,
        SalesOrderStatus.InProgress, data.total, data.items, newDate, newDate)
      dao.createSalesOrder(order) pipeTo self
      goto(WritingEntity) using data

    case Event(FullResult(txn: CreditCardTransaction),
      data: LookedUpData) =>
      data.originator ! Failure(FailureType.Validation,
        CreditRejectedError)
      stop
  }

  when(WritingEntity, ResolveTimeout) {
    case Event(ord: SaleOrder, data: LookedUpData) =>
      data.originator ! FullResult(ord)
      stop
    case Event(Status.Failure(ex: OrderManagerDao.InventoryNotAvailaleException),
      data: LookedUpData) =>
      data.originator ! Failure(
        FailureType.Validation, InventoryNotAvailError)
      stop
    case Event(Status.Failure(ex), data: LookedUpData) =>
      data.originator ! unexpectedFail
      stop
  }

  whenUnhandled {
    case e @ Event(StateTimeout, data) =>
      log.error("State timeout when in state {}", stateName)
      data.originator ! unexpectedFail
      stop
    case e @ Event(other, data) =>
      log.error("Unexpected result of {} when in state {}",
        other, stateName)
      data.originator ! unexpectedFail
      stop
  }

  def newDate = new Date
  def lookup(name: String) = context.actorSelection(s"/user/$name")
  def unexpectedFail = Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
}
