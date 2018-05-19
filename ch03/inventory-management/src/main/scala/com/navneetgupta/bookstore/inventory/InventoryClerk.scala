package com.navneetgupta.bookstore.inventory

import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityAggregate
import java.util.Date
import akka.util.Timeout
import com.navneetgupta.bookstore.common.ServiceResult
import scala.concurrent.Future

object InventoryClerk {
  def props = Props[InventoryClerk]
  val Name = "inventory-clerk"

  //Lookup operations
  case class FindBook(id: Int)
  //case class FindBooksForIds(ids: Seq[Int])
  case class FindBooksByTags(tags: Seq[String])
  case class FindBooksByTitle(title: String)
  case class FindBooksByAuthor(author: String)

  //Modify operations
  case class CreateBook(title: String, author: String, tags: List[String], cost: Double)
  case class AddTagToBook(bookId: Int, tag: String)
  case class RemoveTagFromBook(bookId: Int, tag: String)
  case class AddInventoryToBook(bookId: Int, amount: Int)
  case class DeleteBook(id: Int)

  //Events
  case class OrderCreated(id: Int, books: List[(Int, Int)])
  case class InventoryAllocated(orderId: Int)
  case class InventoryBackOrdered(orderId: Int)
}

class InventoryClerk extends EntityAggregate[BookFO, Book] {
  import context.dispatcher
  import InventoryClerk._
  import com.navneetgupta.bookstore.common.EntityActor._

  val repo = new BookRepository

  //Listen for the OrderCreatd event
  context.system.eventStream.subscribe(self, classOf[OrderCreated])

  override def receive = {
    case FindBook(id) =>
      log.info("Finding book {}", id)
      val book = lookupOrCreateChild(id)
      book.forward(GetFieldsObject)
    case FindBooksByTags(tags) =>
      log.info("Finding books for tags {}", tags)
      val result = multiEntityLookup(repo.findBookIdsByTags(tags))
      pipeResponse(result)
    case FindBooksByTitle(title) =>
      log.info("Finding book for title {}", title)
      val result = multiEntityLookup(repo.findBookIdsByTitle(title))
      pipeResponse(result)

    case FindBooksByAuthor(author) =>
      log.info("Finding book for author {}", author)
      val result = multiEntityLookup(repo.findBookIdsByAuthor(author))
      pipeResponse(result)
    case CreateBook(title, author, tags, cost) =>
      log.info("Creating new book with title {}", title)
      val vo = BookFO(0, title, author, tags, cost, 0, new Date, new Date)
      persistOperation(vo.id, vo)
    case AddInventoryToBook(id, amount) =>
      persistOperation(id, Book.AddInventory(amount))
    case AddTagToBook(id, tag) =>
      persistOperation(id, Book.AddTag(tag))
    case RemoveTagFromBook(id, tag) =>
      persistOperation(id, Book.RemoveTag(tag))
    case DeleteBook(id) =>
      persistOperation(id, Delete)

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
              val f = (lookupOrCreateChild(bookId) ? Book.AllocateInventory(quant)).mapTo[ServiceResult[BookFO]]
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

  def entityProps(id: Int) = Book.props(id)
}
