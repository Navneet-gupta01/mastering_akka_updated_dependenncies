package com.navneetgupta.bookstore.order

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import spray.json._
import com.navneetgupta.bookstore.order.SalesOrderViewBuilder._
import com.navneetgupta.bookstore.credit.CreditCardInfo
import com.navneetgupta.bookstore.order.SalesOrder.LineItemRequest
import com.navneetgupta.bookstore.order.SalesAssociate.CreateNewOrder

trait SalesOrderJsonProtocol extends BookstoreJsonProtocol {
  implicit object LineItemStatusFormatter extends RootJsonFormat[LineItemStatus.Value] {
    override def write(status: LineItemStatus.Value) = {
      JsString(status.toString)
    }
    override def read(jv: JsValue) = jv match {
      case JsString(s) => LineItemStatus.withName(s)
      case other       => throw new DeserializationException(s"expected JsString but got $other")
    }
  }
  implicit val lineItemFoFormat = jsonFormat5(SalesOrderLineItemFO)
  implicit val orderFoFormat = jsonFormat8(SalesOrderFO.apply)
  implicit val lineItemBookFormat = jsonFormat4(LineItemBook)
  implicit val salesOrderLineItemFormat = jsonFormat5(SalesOrderLineItem)
  implicit val orderRmFormat = jsonFormat7(SalesOrderRM)

  implicit val ccInfoFormat = jsonFormat4(CreditCardInfo)
  implicit val lineItemReqFormat = jsonFormat2(LineItemRequest)
  implicit val createNewOrderFormat = jsonFormat4(CreateNewOrder)
}
