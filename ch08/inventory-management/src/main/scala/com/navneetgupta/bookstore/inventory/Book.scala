package com.navneetgupta.bookstore.inventory

import java.util.Date
import com.navneetgupta.bookstore.common.EntityFieldsObject
import com.navneetgupta.bookstore.common.EntityActor
import com.navneetgupta.bookstore.common.ErrorMessage
import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityActor.ErrorMapper
import com.navneetgupta.bookstore.common.Failure
import com.navneetgupta.bookstore.common.FailureType
import com.navneetgupta.bookstore.common.EntityActor.InitializedData
import com.navneetgupta.bookstore.common.PersistentEntity
import com.navneetgupta.bookstore.common.EntityEvent
import com.navneetgupta.bookstore.common.DatamodelReader
import com.navneetgupta.bookstore.common.EntityCommand

object BookFO {
  def empty = new BookFO("", "", "", Nil, 0.0, 0, new Date(0), new Date(0))
}
case class BookFO(id: String, title: String, author: String, tags: List[String], cost: Double,
                  inventoryAmount: Int, createTs: Date, modifyTs: Date = new Date(), deleted: Boolean = false) extends EntityFieldsObject[String, BookFO] {
  def assignId(id: String) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
}

object Book {

  import collection.JavaConversions._

  val EntityType = "book"
  def props = Props[Book]
  val InventoryNotAvailError = ErrorMessage("inventory.notavailable", Some("Inventory for an item on an order can not be allocated"))
  val BookAlreadyCreated = ErrorMessage("book.alreadyexists", Some("This book has already been created and can not handle another CreateBook request"))

  object Command {
    case class CreateBook(book: BookFO) extends EntityCommand {
      def entityId = book.id
    }
    trait BookIdEntityCommand extends EntityCommand { val bookId: String; def entityId = bookId }
    case class AddTag(tag: String, bookId: String) extends BookIdEntityCommand
    case class RemoveTag(tag: String, bookId: String) extends BookIdEntityCommand
    case class AddInventory(amount: Int, bookId: String) extends BookIdEntityCommand
    case class AllocateInventory(orderId: String, amount: Int, bookId: String) extends BookIdEntityCommand
  }

  object Event {
    trait BookEvent extends EntityEvent { def entityType = EntityType }
    case class BookCreated(book: BookFO) extends BookEvent {
      def toDatamodel = {
        println("To DataModel BookCreated")
        val bookDM = Datamodel.Book.newBuilder().
          setId(book.id).
          setTitle(book.title).
          setAuthor(book.author).
          addAllTag(book.tags).
          setCost(book.cost).
          setInventoryAmount(book.inventoryAmount).
          setCreateTs(book.createTs.getTime).
          setModifyTs(book.modifyTs.getTime).
          setDeleted(book.deleted).
          build

        Datamodel.BookCreated.newBuilder.
          setBook(bookDM).
          build
      }
    }

    object BookCreated extends DatamodelReader {
      def fromDatamodel = {
        case bc: Datamodel.BookCreated =>
          println("Form DataModel BookCreated")
          val bookDm = bc.getBook()
          val book = BookFO(bookDm.getId(), bookDm.getTitle(), bookDm.getAuthor(),
            bookDm.getTagList().toList, bookDm.getCost(), bookDm.getInventoryAmount(),
            new Date(bookDm.getCreateTs()), new Date(bookDm.getModifyTs()), bookDm.getDeleted())
          BookCreated(book)
      }
    }
    case class TagAdded(tag: String) extends BookEvent {
      def toDatamodel = {
        println("To DataModel TagAdded")
        Datamodel.TagAdded.newBuilder().
          setTag(tag).
          build
      }
    }
    object TagAdded extends DatamodelReader {
      def fromDatamodel = {
        case ta: Datamodel.TagAdded =>
          println("Form DataModel TagAdded")
          TagAdded(ta.getTag())
      }
    }
    case class TagRemoved(tag: String) extends BookEvent {
      def toDatamodel = {
        println("To DataModel TagRemoved")
        Datamodel.TagRemoved.newBuilder().
          setTag(tag).
          build
      }
    }
    object TagRemoved extends DatamodelReader {
      def fromDatamodel = {
        case ta: Datamodel.TagRemoved =>
          println("From DataModel TagRemoved")
          TagRemoved(ta.getTag())
      }
    }
    case class InventoryAdded(amount: Int) extends BookEvent {
      def toDatamodel = {
        println("To DataModel InventoryAdded")
        Datamodel.InventoryAdded.newBuilder().
          setAmount(amount).
          build
      }
    }
    object InventoryAdded extends DatamodelReader {
      def fromDatamodel = {
        case ia: Datamodel.InventoryAdded =>
          println("Form DataModel InventoryAdded")
          InventoryAdded(ia.getAmount())
      }
    }

    case class InventoryAllocated(orderId: String, bookId: String, amount: Int) extends BookEvent {
      def toDatamodel = {
        println("To DataModel InventoryAllocated")
        Datamodel.InventoryAllocated.newBuilder().
          setOrderId(orderId).
          setAmount(amount).
          setBookId(bookId).
          build
      }
    }
    object InventoryAllocated extends DatamodelReader {
      def fromDatamodel = {
        case ia: Datamodel.InventoryAllocated =>
          println("Form DataModel InventoryAllocated")
          InventoryAllocated(ia.getOrderId(), ia.getBookId(), ia.getAmount())
      }
    }

    case class InventoryBackordered(orderId: String, bookId: String) extends BookEvent {
      def toDatamodel = {
        println("To DataModel InventoryBackordered")
        Datamodel.InventoryBackordered.newBuilder().
          setOrderId(orderId).
          setBookId(bookId).
          build
      }
    }
    object InventoryBackordered extends DatamodelReader {
      def fromDatamodel = {
        case ib: Datamodel.InventoryBackordered =>
          println("Form DataModel InventoryBackordered")
          InventoryBackordered(ib.getOrderId(), ib.getBookId())
      }
    }

    case class BookDeleted(id: String) extends BookEvent {
      def toDatamodel = {
        println("To DataModel BookDeleted")
        Datamodel.BookDeleted.newBuilder().
          setId(id).
          build

      }
    }
    object BookDeleted extends DatamodelReader {
      def fromDatamodel = {
        case bd: Datamodel.BookDeleted =>
          println("Form DataModel BookDeleted")
          BookDeleted(bd.getId())
      }
    }
  }
}

private[inventory] class Book extends PersistentEntity[BookFO] {

  import Book.Command._
  import Book.Event._
  import Book._
  import context.dispatcher

  override def snapshotAfterCount = Some(5)

  def initialState: BookFO = BookFO.empty

  override def additionalCommandHandling: Receive = {
    case CreateBook(book) =>
      if (state != initialState) {
        sender() ! Failure(
          FailureType.Validation, BookAlreadyCreated)
      } else {
        persist(BookCreated(book))(handleEventAndRespond())
      }
    case AddTag(tag, id) =>
      if (state.tags.contains(tag)) {
        log.warning("Not adding tag {} to book {}, tag already exists", tag, state.id)
        sender() ! stateResponse()
      } else
        persist(TagAdded(tag))(handleEventAndRespond())
    case RemoveTag(tag, id) =>
      if (!state.tags.contains(tag)) {
        log.warning("Cannot remove tag {} to book {}, tag not present", tag, state.id)
        sender() ! stateResponse()
      } else
        persist(TagRemoved(tag))(handleEventAndRespond())
    case AddInventory(amount, id) =>
      persist(InventoryAdded(amount))(handleEventAndRespond())
    case AllocateInventory(orderId, amount, idInput) =>
      val event =
        if (amount > state.inventoryAmount) {
          InventoryBackordered(orderId, idInput)
        } else {
          InventoryAllocated(orderId, idInput, amount)
        }
      persist(event) { ev =>
        ev match {
          case bo: InventoryBackordered =>
            handleEvent(ev)
            sender() ! Failure(FailureType.Validation, InventoryNotAvailError)

          case _ =>
            handleEventAndRespond()(ev)
        }
      }

  }

  def handleEvent(event: EntityEvent) = event match {
    case BookCreated(book) =>
      state = book
    case TagAdded(tag) =>
      state = state.copy(tags = tag :: state.tags)
    case TagRemoved(tag) =>
      state = state.copy(tags = state.tags.filter(_ != tag))
    case InventoryAdded(amount) =>
      state = state.copy(inventoryAmount = state.inventoryAmount + amount)
    case InventoryAllocated(orderId, bookId, amount) =>
      state = state.copy(inventoryAmount = state.inventoryAmount - amount)
    case BookDeleted(id) =>
      state = state.markDeleted
    case InventoryBackordered(orderId, bookId) =>
    //
  }

  override def newDeleteEvent = Some(BookDeleted(id))

  def isCreateMessage(cmd: Any): Boolean = cmd match {
    case cr: CreateBook => true
    case _              => false
  }

}
