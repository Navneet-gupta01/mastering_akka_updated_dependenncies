package com.navneetgupta.bookstore.domain.order

import java.util.Date
import com.navneetgupta.bookstore.domain.credit.CreditCardInfo

object SalesOrderStatus extends Enumeration {
  val InProgress, Shipped, Cancelled = Value
}

case class SaleOrder(id: Int, userId: Int, creditTxnId: Int, status: SalesOrderStatus.Value, totalCost: Double, lineItems: List[SalesOrderLineItem], createTs: Date, modifyTs: Date)
case class SalesOrderLineItem(id: Int, orderId: Int, bookId: Int, quantity: Int, cost: Double, createTs: Date, modifyTs: Date)

case class FindOrderById(id: Int)
case class FindOrderForBook(bookId: Int)
case class FindOrderForUser(userId: Int)
case class FindOrderByBookTag(tag: String)

case class LineItemRequest(bookId: Int, quantity: Int)
case class CreateOrder(userId: Int, lineItems: List[LineItemRequest], cardInfo: CreditCardInfo)
