package com.navneetgupta.bookstore.order

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import spray.json._
import com.navneetgupta.bookstore.order.SalesOrderViewBuilder._
import com.navneetgupta.bookstore.credit.CreditCardInfo
import com.navneetgupta.bookstore.order.SalesOrder.LineItemRequest
import com.navneetgupta.bookstore.order.SalesAssociate.CreateNewOrder
import java.text.SimpleDateFormat
import scala.util.Try
import java.util.Date

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
  implicit object DateFormatter extends JsonFormat[Date] {
    override def write(date: Date) = {
      JsString(dateToIsoString(date))
    }
    override def read(jv: JsValue) = jv match {
      case JsNumber(n) => new Date(n.longValue())
      case JsString(s) =>
        parseIsoDateString(s)
          .fold(deserializationError(s"Expected ISO Date format, got $s"))(identity)
      case other => throw new DeserializationException(s"expected JsString but got $other")
    }
  }

  private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  }

  private def dateToIsoString(date: Date) =
    date match {
      case null => localIsoDateFormatter.get().format(new Date())
      case _    => localIsoDateFormatter.get().format(date)
    }

  private def parseIsoDateString(date: String): Option[Date] =
    Try {
      date match {
        case null => new Date()
        case _    => localIsoDateFormatter.get().parse(date)
      }
    }.toOption
  implicit val lineItemFoFormat = jsonFormat5(SalesOrderLineItemFO)
  implicit val orderFoFormat = jsonFormat8(SalesOrderFO.apply)
  implicit val lineItemBookFormat = jsonFormat4(LineItemBook)
  implicit val salesOrderLineItemFormat = jsonFormat5(SalesOrderLineItem)
  implicit val orderRmFormat = jsonFormat7(SalesOrderRM)

  implicit val ccInfoFormat = jsonFormat4(CreditCardInfo)
  implicit val lineItemReqFormat = jsonFormat2(LineItemRequest)
  implicit val createNewOrderFormat = jsonFormat4(CreateNewOrder)
}
