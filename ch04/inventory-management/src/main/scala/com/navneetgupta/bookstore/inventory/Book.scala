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

object BookFO {
  def empty = new BookFO("", "", "", Nil, 0.0, 0, new Date(0), new Date(0))
}
case class BookFO(id: String, title: String, author: String, tags: List[String], cost: Double,
                  inventoryAmount: Int, createTs: Date, modifyTs: Date, deleted: Boolean = false) extends EntityFieldsObject[String, BookFO] {
  def assignId(id: String) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
}

private[bookstore] object Book {

  import collection.JavaConversions._

  def props(id: String) = Props(classOf[Book], id)
  val InventoryNotAvailError = ErrorMessage("inventory.notavailable", Some("Inventory for an item on an order can not be allocated"))
  val BookAlreadyCreated = ErrorMessage("book.alreadyexists", Some("This book has already been created and can not handle another CreateBook request"))

  object Command {
    case class CreateBook(book: BookFO)
    case class AddTag(tag: String)
    case class RemoveTag(tag: String)
    case class AddInventory(amount: Int)
    case class AllocateInventory(orderId: String, amount: Int)
  }

  //  object Event {
  //    case class BookCreated(book: BookFO) extends EntityEvent
  //    case class TagAdded(tag: String) extends EntityEvent
  //    case class TagRemoved(tag: String) extends EntityEvent
  //    case class InventoryAdded(amount: Int) extends EntityEvent
  //    case class InventoryAllocated(orderId: String, amount: Int) extends EntityEvent
  //    case class InventoryBackordered(orderId: String) extends EntityEvent
  //    case class BookDeleted(id: String) extends EntityEvent
  //  }
  case class BookCreated(book: BookFO) extends EntityEvent {
    def toDatamodel = {
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
        val bookDm = bc.getBook()
        val book = BookFO(bookDm.getId(), bookDm.getTitle(), bookDm.getAuthor(),
          bookDm.getTagList().toList, bookDm.getCost(), bookDm.getInventoryAmount(),
          new Date(bookDm.getCreateTs()), new Date(bookDm.getCreateTs()), bookDm.getDeleted())
        BookCreated(book)
    }
  }
  case class TagAdded(tag: String) extends EntityEvent {
    def toDatamodel = {
      Datamodel.TagAdded.newBuilder().
        setTag(tag).
        build
    }
  }
  object TagAdded extends DatamodelReader {
    def fromDatamodel = {
      case ta: Datamodel.TagAdded =>
        TagAdded(ta.getTag())
    }
  }
  case class TagRemoved(tag: String) extends EntityEvent {
    def toDatamodel = {
      Datamodel.TagRemoved.newBuilder().
        setTag(tag).
        build
    }
  }
  object TagRemoved extends DatamodelReader {
    def fromDatamodel = {
      case ta: Datamodel.TagRemoved =>
        TagAdded(ta.getTag())
    }
  }
  case class InventoryAdded(amount: Int) extends EntityEvent {
    def toDatamodel = {
      Datamodel.InventoryAdded.newBuilder().
        setAmount(amount).
        build
    }
  }
  object InventoryAdded extends DatamodelReader {
    def fromDatamodel = {
      case ia: Datamodel.InventoryAdded =>
        InventoryAdded(ia.getAmount())
    }
  }

  case class InventoryAllocated(orderId: String, amount: Int) extends EntityEvent {
    def toDatamodel = {
      Datamodel.InventoryAllocated.newBuilder().
        setOrderId(orderId).
        setAmount(amount).
        build
    }
  }
  object InventoryAllocated extends DatamodelReader {
    def fromDatamodel = {
      case ia: Datamodel.InventoryAllocated =>
        InventoryAllocated(ia.getOrderId(), ia.getAmount())
    }
  }

  case class InventoryBackordered(orderId: String) extends EntityEvent {
    def toDatamodel = {
      Datamodel.InventoryBackordered.newBuilder().
        setOrderId(orderId).
        build
    }
  }
  object InventoryBackordered extends DatamodelReader {
    def fromDatamodel = {
      case ib: Datamodel.InventoryBackordered =>
        InventoryBackordered(ib.getOrderId())
    }
  }

  case class BookDeleted(id: String) extends EntityEvent {
    def toDatamodel = {
      Datamodel.BookDeleted.newBuilder().
        setId(id).
        build

    }
  }
  object BookDeleted extends DatamodelReader {
    def fromDatamodel = {
      case bd: Datamodel.BookDeleted =>
        BookDeleted(bd.getId())
    }
  }
}

private[inventory] class Book(idInput: String) extends PersistentEntity[BookFO](idInput) {

  import Book.Command._
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
    case AddTag(tag) =>
      if (state.tags.contains(tag))
        sender() ! stateResponse()
      else
        persist(TagAdded(tag))(handleEventAndRespond())
    case RemoveTag(tag) =>
      if (!state.tags.contains(tag))
        sender() ! stateResponse()
      else
        persist(TagRemoved(tag))(handleEventAndRespond())
    case AddInventory(amount) =>
      persist(InventoryAdded(amount))(handleEventAndRespond())
    case AllocateInventory(orderId, amount) =>
      val event =
        if (amount > state.inventoryAmount) {
          InventoryBackordered(orderId)
        } else {
          InventoryAllocated(orderId, amount)
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
    case InventoryAllocated(orderId, amount) =>
      state = state.copy(inventoryAmount = state.inventoryAmount - amount)
    case BookDeleted(id) =>
      state = state.markDeleted
    case InventoryBackordered(orderId) =>
    //
  }

  override def newDeleteEvent = Some(BookDeleted(idInput))

  def isCreateMessage(cmd: Any): Boolean = cmd match {
    case cr: CreateBook => true
    case _              => false
  }

}
