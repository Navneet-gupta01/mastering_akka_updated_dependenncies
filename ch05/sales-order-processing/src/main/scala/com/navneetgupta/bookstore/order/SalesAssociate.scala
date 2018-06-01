package com.navneetgupta.bookstore.order

import akka.actor.Props
import com.navneetgupta.bookstore.common._
import java.util.UUID
import com.navneetgupta.bookstore.credit.CreditCardInfo
import com.navneetgupta.bookstore.credit.CreditCardInfo
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.NoOffset
import akka.persistence.query.Sequence
import com.navneetgupta.bookstore.inventory.Book.Event.InventoryAllocated
import com.navneetgupta.bookstore.inventory.Book.Event.InventoryBackordered
import akka.persistence.query.EventEnvelope

object SalesAssociate {
  val Name = "sales-associate"
  def props = Props[SalesAssociate]

  case class CreateNewOrder(userEmail: String, lineItems: List[SalesOrder.LineItemRequest], cardInfo: CreditCardInfo, id: Option[String])
  case class FindOrderById(id: String)
  //  case class FindOrdersForBook(bookId: String)
  //  case class FindOrdersForUser(userId: String)
  //  case class FindOrdersForBookTag(tag: String)
}
class SalesAssociate extends Aggregate[SalesOrderFO, SalesOrder] {
  import context.dispatcher
  import SalesAssociate._
  import PersistentEntity._

  import SalesOrder.Command._

  //  context.system.eventStream.subscribe(self, classOf[InventoryAllocated])
  //  context.system.eventStream.subscribe(self, classOf[InventoryBackOrdered])

  val projection = ResumableProjection("order-status", context.system)
  implicit val mater = ActorMaterializer()
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  projection.fetchLatestOffset.foreach { offset =>
    offset match {
      case NoOffset =>
        log.info("Order status projection using an offset of: {}", new java.util.Date(0L))
      case Sequence(x) => log.info("Order status projection using an offset of: {}", new java.util.Date(x))
    }

    journal.
      eventsByTag("book", offset).
      runForeach(e => self ! e)
  }

  def receive = {
    case FindOrderById(id) =>
      val salesOrder = lookupOrCreateChild(id)
      salesOrder.forward(GetState)
    case req: CreateNewOrder =>
      log.info("Creating new Order")
      val newId = UUID.randomUUID().toString
      val entity = lookupOrCreateChild(newId)
      val command = CreateOrder(newId, req.userEmail, req.lineItems, req.cardInfo)
      entity.forward(command)

    case EventEnvelope(offset, pid, seq, event) =>
      event match {
        case InventoryAllocated(orderId, bookId, amount) =>
          forwardCommand(orderId, UpdateLineItemStatus(bookId, LineItemStatus.Approved))

        case InventoryBackordered(orderId, bookId) =>
          forwardCommand(orderId, UpdateLineItemStatus(bookId, LineItemStatus.BackOrdered))

        case other =>
      }
      projection.storeLatestOffset(offset)
  }

  def entityProps(id: String) = SalesOrder.props(id)
}
