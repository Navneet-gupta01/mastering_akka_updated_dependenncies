package com.navneetgupta.bookstore.inventory

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.inventory.BookViewBuilder.BookRM
import com.navneetgupta.bookstore.inventory.InventoryClerk.CreateBook
import spray.json.RootJsonFormat
import java.util.Date
import spray.json.JsString
import spray.json.JsValue
import java.text.DateFormat
import spray.json.DeserializationException
import java.text.SimpleDateFormat
import scala.util.Try
import spray.json._

trait InventoryJsonProtocol extends BookstoreJsonProtocol {

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

  implicit val bookFoFormat = jsonFormat9(BookFO.apply)
  implicit val bookRmFormat = jsonFormat8(BookRM.apply)
  implicit val catalogBookFormat = jsonFormat4(CreateBook.apply)

}
