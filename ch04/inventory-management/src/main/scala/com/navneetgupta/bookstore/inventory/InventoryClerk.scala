package com.navneetgupta.bookstore.inventory

import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityAggregate
import java.util.Date
import akka.util.Timeout
import com.navneetgupta.bookstore.common.ServiceResult
import scala.concurrent.Future
import com.navneetgupta.bookstore.common.Aggregate
import java.util.UUID
import com.navneetgupta.bookstore.common.PersistentEntity.MarkAsDeleted
import com.navneetgupta.bookstore.common.PersistentEntity.GetState

object InventoryClerk {
  def props = Props[InventoryClerk]
  val Name = "inventory-clerk"

  //Lookup operations
  case class FindBook(id: String)
  //case class FindBooksForIds(ids: Seq[String])
  case class FindBooksByTags(tags: Seq[String])
  case class FindBooksByTitle(title: String)
  case class FindBooksByAuthor(author: String)

  //Modify operations
  case class CreateBook(title: String, author: String, tags: List[String], cost: Double)
  case class AddTagToBook(bookId: String, tag: String)
  case class RemoveTagFromBook(bookId: String, tag: String)
  case class AddInventoryToBook(bookId: String, amount: Int)
  case class DeleteBook(id: String)

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
  context.system.eventStream.subscribe(self, classOf[OrderCreated])

  override def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      val book = lookupOrCreateChild(id)
      book.forward(GetState)
    //    case FindBooksByTags(tags) =>
    //      log.info("Finding books for tags {}", tags)
    //      val result = multiEntityLookup(repo.findBookIdsByTags(tags))
    //      pipeResponse(result)
    //    case FindBooksByTitle(title) =>
    //      log.info("Finding book for title {}", title)
    //      val result = multiEntityLookup(repo.findBookIdsByTitle(title))
    //      pipeResponse(result)

    //    case FindBooksByAuthor(author) =>
    //      log.info("Finding book for author {}", author)
    //      val result = multiEntityLookup(repo.findBookIdsByAuthor(author))
    //      pipeResponse(result)
    case CreateBook(title, author, tags, cost) =>
      log.info("Creating new book with title {}", title)
      val id = UUID.randomUUID().toString()
      val vo = BookFO(id, title, author, tags, cost, 0, new Date, new Date)
      val command = Book.Command.CreateBook(vo)
      forwardCommand(id, command)
    case AddInventoryToBook(id, amount) =>
      val command = Book.Command.AddInventory(amount)
      forwardCommand(id, command)
    case AddTagToBook(id, tag) =>
      forwardCommand(id, Book.Command.AddTag(tag))
    case RemoveTagFromBook(id, tag) =>
      forwardCommand(id, Book.Command.RemoveTag(tag))
    case DeleteBook(id) =>
      forwardCommand(id, MarkAsDeleted)

    case OrderCreated(id, lineItems) =>
      import akka.pattern.ask
      import concurrent.duration._
      implicit val timeout = Timeout(5 seconds)

      //Allocate inventory from each book
      log.info("Received OrderCreated event for order id {}", id)
      val futs =
        lineItems.
          map {
            case (bookId, quant) =>
              val f = (lookupOrCreateChild(bookId) ? Book.Command.AllocateInventory(id, quant)).mapTo[ServiceResult[BookFO]]
              f.filter(_.isValid)
          }

      //If we get even one failure, consider it backordered
      Future.sequence(futs).
        map { _ =>
          log.info("Inventory available for order {}", id)
          InventoryAllocated(id)
        }.
        recover {
          case ex =>
            log.warning("Inventory back ordered for order {}", id)
            InventoryBackOrdered(id)
        }.
        foreach(context.system.eventStream.publish)
  }

  def entityProps(id: String) = Book.props(id)
}
