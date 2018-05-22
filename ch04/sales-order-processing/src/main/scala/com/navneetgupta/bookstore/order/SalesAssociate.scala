package com.navneetgupta.bookstore.order

import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityAggregate
import com.navneetgupta.bookstore.inventory.InventoryClerk.InventoryAllocated
import com.navneetgupta.bookstore.inventory.InventoryClerk.InventoryBackOrdered
import com.navneetgupta.bookstore.common._
import java.util.UUID
import com.navneetgupta.bookstore.credit.CreditCardInfo
import com.navneetgupta.bookstore.credit.CreditCardInfo

object SalesAssociate {
  val Name = "sales-associate"
  def props = Props[SalesAssociate]

  case class CreateNewOrder(userEmail: String, lineItems: List[SalesOrder.LineItemRequest], cardInfo: CreditCardInfo)
  case class FindOrderById(id: String)
  case class FindOrdersForBook(bookId: String)
  case class FindOrdersForUser(userId: String)
  case class FindOrdersForBookTag(tag: String)
}
class SalesAssociate extends Aggregate[SalesOrderFO, SalesOrder] {
  import context.dispatcher
  import SalesAssociate._
  import PersistentEntity._

  import SalesOrder.Command._

  context.system.eventStream.subscribe(self, classOf[InventoryAllocated])
  context.system.eventStream.subscribe(self, classOf[InventoryBackOrdered])

  def receive = {
    case FindOrderById(id) =>
      val salesOrder = lookupOrCreateChild(id)
      salesOrder.forward(GetState)
    //    case FindOrdersForBook(bookId) =>
    //      val result = multiEntityLookup(repo.findOrderIdsForBook(bookId))
    //      pipeResponse(result)
    //    case FindOrdersForBookTag(tag) =>
    //      val result = multiEntityLookup(repo.findOrderIdsForBookTag(tag))
    //      pipeResponse(result)
    //    case FindOrdersForUser(userId) =>
    //      val result = multiEntityLookup(repo.findOrderIdsForUser(userId))
    //      pipeResponse(result)

    case req: CreateNewOrder =>
      log.info("Creating new Order")
      val newId = UUID.randomUUID().toString
      val entity = lookupOrCreateChild(newId)
      val command = CreateOrder(newId, req.userEmail, req.lineItems, req.cardInfo)
      entity.forward(command)

    case InventoryAllocated(id) =>
      forwardCommand(id, UpdateOrderStatus(SalesOrderStatus.Approved))

    case InventoryBackOrdered(id) =>
      forwardCommand(id, UpdateOrderStatus(SalesOrderStatus.BackOrdered))
  }

  def entityProps(id: String) = SalesOrder.props(id)
}
