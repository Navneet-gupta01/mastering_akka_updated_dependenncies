package com.navneetgupta.bookstore.inventory

import akka.actor.Props
import java.util.Date
import akka.util.Timeout
import com.navneetgupta.bookstore.common.ServiceResult
import scala.concurrent.Future
import com.navneetgupta.bookstore.common.Aggregate
import java.util.UUID
import com.navneetgupta.bookstore.common.PersistentEntity.MarkAsDeleted
import com.navneetgupta.bookstore.common.PersistentEntity.GetState
import akka.persistence.query.EventEnvelope
import com.navneetgupta.bookstore.common.ResumableProjection
import akka.stream.ActorMaterializer
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.NoOffset
import akka.persistence.query.TimeBasedUUID
import com.navneetgupta.bookstore.common.BookstoreActor
import akka.actor.ActorRef
import com.navneetgupta.bookstore.inventory.Book.Command.AllocateInventory

object InventoryClerk {
  def props = Props[InventoryClerk]
  val Name = "inventory-clerk"

  //Lookup operations
  case class FindBook(id: String)
  case class FindBooksByTitle(title: String)

  //Modify operations
  case class CreateBook(title: String, author: String, tags: List[String], cost: Double)
  case class AddTagToBook(bookId: String, tag: String)
  case class RemoveTagFromBook(bookId: String, tag: String)
  case class AddInventoryToBook(bookId: String, amount: Int)
  case class DeleteBook(id: String)

  trait SalesOrderCreateInfo {
    def id: String
    def lineItemInfo: List[(String, Int)]
  }

  //Events
  case class OrderCreated(id: String, books: List[(String, Int)])
  case class InventoryAllocated(orderId: String)
  case class InventoryBackOrdered(orderId: String)
}

class InventoryClerk extends Aggregate[BookFO, Book] {
  import context.dispatcher
  import InventoryClerk._
  import com.navneetgupta.bookstore.common.EntityActor._

  //val repo = new BookRepository

  //Listen for the OrderCreatd event

  def entityProps = Book.props

  override def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      forwardCommand(id, GetState(id))
    case CreateBook(title, author, tags, cost) =>
      log.info("Creating new book with title {}", title)
      val id = UUID.randomUUID().toString()
      val vo = BookFO(id, title, author, tags, cost, 0, new Date, new Date)
      val command = Book.Command.CreateBook(vo)
      forwardCommand(id, command)
    case AddInventoryToBook(id, amount) =>
      val command = Book.Command.AddInventory(amount, id)
      forwardCommand(id, command)
    case AddTagToBook(id, tag) =>
      forwardCommand(id, Book.Command.AddTag(tag, id))
    case RemoveTagFromBook(id, tag) =>
      forwardCommand(id, Book.Command.RemoveTag(tag, id))
    case DeleteBook(id) =>
      forwardCommand(id, MarkAsDeleted)

    case order: SalesOrderCreateInfo =>
      order.lineItemInfo.
        foreach {
          case (bookId, quant) =>
            forwardCommand(bookId, AllocateInventory(order.id, quant, bookId))
        }
  }
}

object InventoryAllocationEventListener {
  val Name = "inventory-allocation-listener"
  def props(clerk: ActorRef) = Props(classOf[InventoryAllocationEventListener], clerk)
}
class InventoryAllocationEventListener(clerk: ActorRef) extends BookstoreActor {
  import InventoryClerk._
  import context.dispatcher
  import com.navneetgupta.bookstore.common.EntityActor._

  val projection = ResumableProjection("inventory-allocation", context.system)
  implicit val mater = ActorMaterializer()
  val journal = PersistenceQuery(context.system).
    readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  projection.fetchLatestOffset.foreach { o =>
    journal.
      eventsByTag("ordercreated", o.getOrElse(NoOffset)).
      runForeach(e => self ! e)
  }

  def receive = {
    case EventEnvelope(offset, pid, seq, order: SalesOrderCreateInfo) =>
      log.info("Received OrderCreated event for order id {}", order.id)
      clerk ! order
      offset match {
        case TimeBasedUUID(x) =>
          projection.storeLatestOffset(x)
      }
  }
}
