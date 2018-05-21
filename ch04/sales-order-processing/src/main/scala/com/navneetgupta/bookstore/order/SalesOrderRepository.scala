package com.navneetgupta.bookstore.order

import com.navneetgupta.bookstore.common.EntityRepository
import scala.concurrent.ExecutionContext
import slick.jdbc.GetResult
import scala.concurrent.Future

object SalesOrderRepository {
  val OrderSelect = "select id, userId, creditTxnId, status, totalCost, createTs, modifyTs from SalesOrderHeader where"
  val LineItemSelect = "select id, orderId, bookId, quantity, cost, createTs, modifyTs from SalesOrderLineItem where "
  implicit val GetOrder = GetResult { r => SalesOrderFO(r.<<, r.<<, r.<<, SalesOrderStatus.withName(r.<<), r.<<, Nil, r.nextTimestamp, r.nextTimestamp) }
  implicit val GetLineItem = GetResult { r => SalesOrderLineItemFO(r.<<, r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp) }
}

class SalesOrderRepository(implicit val ec: ExecutionContext) extends EntityRepository[SalesOrderFO] {
  import DaoHelpers._
  import slick.driver.PostgresDriver.api._
  import slick.dbio.DBIOAction
  import SalesOrderRepository._

  override def deleteEntity(id: Int) = Future.failed(new NotImplementedError("delete not implemented for sales order"))

  override def loadEntity(id: Int) = {
    val headersF = db.run(sql"#$OrderSelect id = $id".as[SalesOrderFO])
    val itemsF = db.run(sql"#$LineItemSelect orderId = $id".as[SalesOrderLineItemFO])

    for {
      header <- headersF
      items <- itemsF
    } yield {
      header.headOption.map(_.copy(lineItems = items.toList))
    }
  }
  override def persistEntity(order: SalesOrderFO) = {
    val insertHeader = sqlu"""
      insert into SalesOrderHeader (userId, creditTxnId, status, totalCost, createTs, modifyTs)
      values (${order.userId}, ${order.creditTxnId}, ${order.status.toString}, ${order.totalCost}, ${order.createTs.toSqlDate}, ${order.modifyTs.toSqlDate})
    """

    val getId = lastIdSelect("salesorderheader")

    def insertLineItems(orderId: Int) = order.lineItems.map { item =>
      sqlu"""
        insert into SalesOrderLineItem (orderId, bookId, quantity, cost, createTs, modifyTs)
        values ($orderId, ${item.bookId}, ${item.quantity}, ${item.cost}, ${item.createTs.toSqlDate}, ${item.modifyTs.toSqlDate})
      """
    }

    val txn =
      for {
        _ <- insertHeader
        id <- getId
        if id.headOption.isDefined
        _ <- DBIOAction.sequence(insertLineItems(id.head))
      } yield {
        id.head
      }

    db.run(txn.transactionally)
  }

  def findOrderIdsForUser(userId: Int) = {
    val select = sql"select id from SalesOrderHeader where userId = $userId".as[Int]
    db.run(select)
  }

  def findOrderIdsForBook(bookId: Int) = {
    val select = sql"select distinct(orderId) from SalesOrderLineItem where bookId = $bookId".as[Int]
    db.run(select)
  }

  def findOrderIdsForBookTag(tag: String) = {
    val select = sql"select distinct(l.orderId) from SalesOrderLineItem l left join BookTag t on l.bookId = t.bookId where t.tag = $tag".as[Int]
    db.run(select)
  }

  def updateOrderStatus(id: Int, status: SalesOrderStatus.Value) = {
    db.run(sqlu"update SalesOrderHeader set status = ${status.toString()} where id = $id")
  }

}
