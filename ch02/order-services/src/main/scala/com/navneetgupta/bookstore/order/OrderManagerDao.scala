package com.navneetgupta.bookstore.order

import slick.jdbc.GetResult
import com.navneetgupta.bookstore.domain.order._
import com.navneetgupta.bookstore.common.BookStoreDao
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import slick.jdbc.SQLActionBuilder
import slick.dbio.DBIOAction

object OrderManagerDao {
  class InventoryNotAvailaleException extends Exception
  val BaseSelect = "select id, userId, creditTxnId, status, totalCost, createTs, modifyTs from SalesOrderHeader where"
  implicit val GetOrder = GetResult { r => SaleOrder(r.<<, r.<<, r.<<, SalesOrderStatus.withName(r.<<), r.<<, Nil, r.nextTimestamp, r.nextTimestamp) }
  implicit val GetLineItem = GetResult { r => SalesOrderLineItem(r.<<, r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp) }
}
class OrderManagerDao(implicit val ec: ExecutionContext) extends BookStoreDao {
  import slick.driver.PostgresDriver.api._
  import OrderManagerDao._
  import DaoHelpers._

  def findOrderById(id: Int) = findOrdersByIds(Set(id)).map(_.headOption)

  def findOrdersByIds(ids: Set[Int]) = {
    if (ids.isEmpty) Future.successful(Vector.empty)
    else {
      val idsInput = ids.mkString(",")
      val select = sql"#$BaseSelect id in (#$idsInput)"
      findOrdersByCriteria(select)
    }
  }

  private def findOrdersByCriteria(orderSelect: SQLActionBuilder) = {
    val headersF = db.run(orderSelect.as[SaleOrder])
    def selectItems(orderIds: Seq[Int]): Future[Vector[SalesOrderLineItem]] = {
      if (orderIds.isEmpty) Future.successful(Vector.empty)
      else db.run(sql"select id, orderId, bookId, quantity, cost, createTs, modifyTs from SalesOrderLineItem where orderId in (#${orderIds.mkString(",")})".as[SalesOrderLineItem])
    }
    for {
      headers <- headersF
      items <- selectItems(headers.map(_.id))
    } yield {
      val itemsByOrder = items.groupBy(_.orderId)
      headers.map(o => o.copy(lineItems = itemsByOrder.get(o.id).map(_.toList).getOrElse(Nil)))
    }
  }

  def findOrdersForUser(userId: Int) = {
    val select = sql"#$BaseSelect userId = $userId"
    findOrdersByCriteria(select)
  }

  def findOrderIdsForBook(bookId: Int) = {
    val select = sql"select distinct(orderId) from SalesOrderLineItem where bookId = $bookId"
    db.run(select.as[Int])
  }

  def findOrderIdsForBookTag(tag: String) = {
    val select = sql"select distinct(l.orderId) from SalesOrderLineItem l right join BookTag t on l.bookId = t.bookId where t.tag = $tag"
    db.run(select.as[Int])
  }

  def createSalesOrder(order: SaleOrder) = {
    val insertHeader = sqlu"""
      insert into SalesOrderHeader (userId, creditTxnId, status, totalCost, createTs, modifyTs)
      values (${order.userId}, ${order.creditTxnId}, ${order.status.toString}, ${order.totalCost}, ${order.createTs.toSqlDate}, ${order.modifyTs.toSqlDate})
    """

    val getId = lastIdSelect("salesorderheader")

    def insertLineItems(orderId: Int) = order.lineItems.map { item =>
      val insert =
        sqlu"""
          insert into SalesOrderLineItem (orderId, bookId, quantity, cost, createTs, modifyTs)
          values ($orderId, ${item.bookId}, ${item.quantity}, ${item.cost}, ${item.createTs.toSqlDate}, ${item.modifyTs.toSqlDate})
        """

      //Using optimistic currency control on the update via the where clause
      val decrementInv =
        sqlu"""
          update Book set inventoryAmount = inventoryAmount - ${item.quantity} where id = ${item.bookId} and inventoryAmount >= ${item.quantity}
        """

      insert.
        andThen(decrementInv).
        filter(_ == 1)
    }

    val txn =
      for {
        _ <- insertHeader
        id <- getId
        if id.headOption.isDefined
        _ <- DBIOAction.sequence(insertLineItems(id.head))
      } yield {
        order.copy(id = id.head)
      }

    db.
      run(txn.transactionally).
      recoverWith {
        case ex: NoSuchElementException => Future.failed(new InventoryNotAvailaleException)
      }
  }

}
