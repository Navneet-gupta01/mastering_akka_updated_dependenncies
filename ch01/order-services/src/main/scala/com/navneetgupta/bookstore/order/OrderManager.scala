package com.navneetgupta.bookstore.order

import com.navneetgupta.bookstore.common.ErrorMessage
import akka.actor.Props
import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.BookStoreActor
import com.navneetgupta.bookstore.domain.order._
import scala.concurrent.Future
import scala.concurrent.duration._
import com.navneetgupta.bookstore.common.FailureType
import java.util.Date
import com.navneetgupta.bookstore.common.Failure
import akka.actor.ActorRef
import com.navneetgupta.bookstore.common.ServiceResult
import com.navneetgupta.bookstore.domain.user.FindUserById
import akka.util.Timeout
import com.navneetgupta.bookstore.domain.user.BookStoreUser
import com.navneetgupta.bookstore.domain.book.FindBook
import com.navneetgupta.bookstore.domain.book.Book
import com.navneetgupta.bookstore.domain.credit.ChargeCreditCard
import com.navneetgupta.bookstore.common.FullResult
import com.navneetgupta.bookstore.domain.credit.CreditCardTransaction
import com.navneetgupta.bookstore.domain.credit.CreditTransactionStatus

object OrderManager {
  def props = Props[OrderManager]
  val Name = "order-manager"
  val BookMgrName = "book-manager"
  val UserManagerName = "user-manager"
  val CreditHandlerName = "credit-handler"

  class OrderProcessingException(val error: ErrorMessage) extends Throwable
  val InvalidBookIdError = ErrorMessage("order.invalid.bookId", Some("You have supplied an invalid book id"))
  val InvalidUserIdError = ErrorMessage("order.invalid.userId", Some("You have supplied an invalid user id"))
  val CreditRejectedError = ErrorMessage("order.credit.rejected", Some("Your credit card has been rejected"))
  val InventoryNotAvailError = ErrorMessage("order.inventory.notavailable", Some("Inventory for an item on this order is no longer available"))
}
class OrderManager extends BookStoreActor {
  import context.dispatcher
  import OrderManager._
  import akka.pattern.ask
  implicit val timeout = Timeout(5 seconds)

  val dao = new OrderManagerDao

  override def receive = {
    case FindOrderById(orderId) =>
      pipeResponse(dao.findOrderById(orderId))
    case FindOrderForBook(bookId) =>
      val result = findForBook(dao.findOrderIdsForBook(bookId))
      pipeResponse(result)
    case FindOrderForUser(userId) =>
      pipeResponse(dao.findOrdersForUser(userId))
    case FindOrderByBookTag(tag) =>
      pipeResponse(findForBook(dao.findOrderIdsForBookTag(tag)))
    case req: CreateOrder =>
      log.info("Creating new sales order processor and forwarding request")
      val result = createOrder(req)
      pipeResponse(result.recover {
        case ex: OrderProcessingException => Failure(FailureType.Validation, ex.error)
      })
  }

  def findForBook(f: => Future[Vector[Int]]) = {
    for {
      orderIds <- f
      orders <- dao.findOrdersByIds(orderIds.toSet)
    } yield orders
  }

  def createOrder(request: CreateOrder): Future[SaleOrder] = {

    //Resolve dependencies in parallel
    val bookMgrFut = lookup(BookMgrName)
    val userMgrFut = lookup(UserManagerName)
    val creditMgrFut = lookup(CreditHandlerName)

    for {
      bookMgr <- bookMgrFut
      userMgr <- userMgrFut
      creditMgr <- creditMgrFut
      (user, lineItems) <- loadUser(request, userMgr).zip(buildLineItems(request, bookMgr))
      total = lineItems.map(_.cost).sum
      creditTxn <- chargeCreditCard(request, total, creditMgr)
      order = SaleOrder(0, user.id, creditTxn.id, SalesOrderStatus.InProgress, total, lineItems, new Date, new Date)
      daoResult <- dao.createSalesOrder(order)
    } yield daoResult

  }

  def loadUser(request: CreateOrder, userMgr: ActorRef) = {
    (userMgr ? FindUserById(request.userId)).
      mapTo[ServiceResult[BookStoreUser]].
      flatMap(unwrapResult(InvalidUserIdError))
  }

  def buildLineItems(request: CreateOrder, bookMgr: ActorRef) = {
    //Lookup Books and map into SalesOrderLineItems, validating that inventory is available for each
    val quantityMap = request.lineItems.map(i => (i.bookId, i.quantity)).toMap

    Future.traverse(request.lineItems) { item =>
      (bookMgr ? FindBook(item.bookId)).
        mapTo[ServiceResult[Book]].
        flatMap(unwrapResult(InvalidBookIdError))
    }.
      flatMap { books =>
        val inventoryAvail = books.forall { b =>
          quantityMap.get(b.id).map(q => b.inventoryAmount >= q).getOrElse(false)
        }
        if (inventoryAvail)
          Future.successful(books.map { b =>
            val quantity = quantityMap.getOrElse(b.id, 0) //safe as we already vetted in the above step
            SalesOrderLineItem(0, 0, b.id, quantity, quantity * b.cost, new Date, new Date)
          })
        else
          Future.failed(new OrderProcessingException(InventoryNotAvailError))
      }
  }

  def chargeCreditCard(request: CreateOrder, total: Double, creditMgr: ActorRef) = {
    (creditMgr ? ChargeCreditCard(request.cardInfo, total)).
      mapTo[ServiceResult[CreditCardTransaction]].
      flatMap(unwrapResult(ServiceResult.UnexpectedFailure)).
      flatMap {
        case txn if txn.status == CreditTransactionStatus.Approved =>
          Future.successful(txn)
        case txn =>
          Future.failed(new OrderProcessingException(CreditRejectedError))
      }
  }

  def unwrapResult[T](error: ErrorMessage)(result: ServiceResult[T]): Future[T] = result match {
    case FullResult(user) => Future.successful(user)
    case other            => Future.failed(new OrderProcessingException(error))
  }

  def lookup(name: String) = context.actorSelection(s"/user/$name").resolveOne(5 seconds)
}
