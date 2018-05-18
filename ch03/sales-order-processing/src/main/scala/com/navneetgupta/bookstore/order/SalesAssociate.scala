package com.navneetgupta.bookstore.order

import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityAggregate
import com.navneetgupta.bookstore.inventory.InventoryClerk.InventoryAllocated
import com.navneetgupta.bookstore.inventory.InventoryClerk.InventoryBackOrdered
import com.navneetgupta.bookstore.common.EntityActor.GetFieldsObject

object SalesAssociate {
  val Name = "sales-associate"
  def props = Props[SalesAssociate]

  case class FindOrderById(id: Int)
  case class FindOrdersForBook(bookId: Int)
  case class FindOrdersForUser(userId: Int)
  case class FindOrdersForBookTag(tag: String)
}
class SalesAssociate extends EntityAggregate[SalesOrderFO, SalesOrder] {
  import context.dispatcher
  import SalesAssociate._

  val repo = new SalesOrderRepository
  context.system.eventStream.subscribe(self, classOf[InventoryAllocated])
  context.system.eventStream.subscribe(self, classOf[InventoryBackOrdered])

  def receive = {
    case FindOrderById(id) =>
      val salesOrder = lookupOrCreateChild(id)
      salesOrder.forward(GetFieldsObject)
    case FindOrdersForBook(bookId) =>
      val result = multiEntityLookup(repo.findOrderIdsForBook(bookId))
      pipeResponse(result)
    case FindOrdersForBookTag(tag) =>
      val result = multiEntityLookup(repo.findOrderIdsForBookTag(tag))
      pipeResponse(result)
    case FindOrdersForUser(userId) =>
      val result = multiEntityLookup(repo.findOrderIdsForUser(userId))
      pipeResponse(result)
  }

  def entityProps(id: Int) = SalesOrder.props(id)
}
