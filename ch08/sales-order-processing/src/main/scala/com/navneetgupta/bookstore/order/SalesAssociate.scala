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
import akka.persistence.query.TimeBasedUUID
import akka.persistence.query.Offset
import com.navneetgupta.bookstore.order.OrderStatusEventListener
import akka.actor.ActorRef
import com.navneetgupta.bookstore.order.SalesAssociate.StatusChange

object SalesAssociate {
  val Name = "sales-associate"
  def props = Props[SalesAssociate]

  case class CreateNewOrder(userEmail: String, lineItems: List[SalesOrder.LineItemRequest], cardInfo: CreditCardInfo, id: Option[String])
  case class FindOrderById(id: String)
  case class StatusChange(orderId: String, status: LineItemStatus.Value, bookId: String, offset: Offset)
}

class SalesAssociate extends Aggregate[SalesOrderFO, SalesOrder] {
  import context.dispatcher
  import SalesAssociate._
  import PersistentEntity._

  import SalesOrder.Command._

  def receive = {
    case FindOrderById(id) =>
      forwardCommand(id, GetState(id))
    case req: CreateNewOrder =>
      log.info("Creating new Order")
      val newId = UUID.randomUUID().toString
      //val entity = lookupOrCreateChild(newId)
      val command = CreateOrder(newId, req.userEmail, req.lineItems, req.cardInfo)
      forwardCommand(newId, command)

    case StatusChange(orderId, status, bookId, offset) =>
      forwardCommand(orderId, UpdateLineItemStatus(bookId, status, orderId))

    //    case EventEnvelope(offset, pid, seq, event) =>
    //      event match {
    //        case InventoryAllocated(orderId, bookId, amount) =>
    //          forwardCommand(orderId, UpdateLineItemStatus(bookId, LineItemStatus.Approved))
    //
    //        case InventoryBackordered(orderId, bookId) =>
    //          forwardCommand(orderId, UpdateLineItemStatus(bookId, LineItemStatus.BackOrdered))
    //
    //        case other =>
    //      }
    //      offset match {
    //        case TimeBasedUUID(x) =>
    //          projection.storeLatestOffset(x)
    //      }
  }

  def entityProps = SalesOrder.props
}

object OrderStatusEventListener {
  val Name = "order-status-event-listener"
  def props(associate: ActorRef) = Props(classOf[OrderStatusEventListener], associate)
}

class OrderStatusEventListener(associate: ActorRef) extends BookstoreActor {
  import context.dispatcher

  val projection = ResumableProjection("order-status", context.system)
  implicit val mater = ActorMaterializer()
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  projection.fetchLatestOffset.foreach { offset =>
    offset.getOrElse(NoOffset) match {
      case NoOffset =>
        log.info("Order status projection using an offset of NoOffset: {}", new java.util.Date(0L))
      case TimeBasedUUID(x) => log.info("Order status projection using an offset of TimeBasedUUID with uuid: {}", x)
    }
    val allocatedSource = journal.eventsByTag("inventoryallocated", offset.getOrElse(NoOffset))
    val backorderedSource = journal.eventsByTag("inventorybackordered", offset.getOrElse(NoOffset))

    allocatedSource.
      merge(backorderedSource).
      collect {
        case EventEnvelope(offset, pid, seq, event: InventoryAllocated) =>
          StatusChange(event.orderId, LineItemStatus.Approved, event.bookId, offset)

        case EventEnvelope(offset, pid, seq, event: InventoryBackordered) =>
          StatusChange(event.orderId, LineItemStatus.BackOrdered, event.bookId, offset)
      }.
      runForeach(self ! _)
  }

  def receive = {
    case change @ StatusChange(orderId, status, bookId, offset) =>
      associate ! change
      offset match {
        case TimeBasedUUID(x) => projection.storeLatestOffset(x)
        case _                =>
      }
  }
}
