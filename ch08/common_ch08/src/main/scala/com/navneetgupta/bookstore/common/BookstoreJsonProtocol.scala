package com.navneetgupta.bookstore.common

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import java.util.Date

trait BookstoreJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object DateFormat extends JsonFormat[Date] {
    override def write(date: Date): JsValue = JsNumber(date.getTime)
    override def read(json: JsValue): Date = json match {
      case JsNumber(epoch) => new Date(epoch.toLong)
      case unknown         => deserializationError(s"Expected JsString, got $unknown")
    }
  }
  //  implicit object DateFormatter extends JsonFormat[Date] {
  //    override def write(date: Date) = {
  //      JsString(dateToIsoString(date))
  //    }
  //    override def read(jv: JsValue) = jv match {
  //      case JsNumber(n) => new Date(n.longValue())
  //      case JsString(s) =>
  //        parseIsoDateString(s)
  //          .fold(deserializationError(s"Expected ISO Date format, got $s"))(identity)
  //      case other => throw new DeserializationException(s"expected JsString but got $other")
  //    }
  //  }
  //
  //  private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
  //    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  //  }
  //
  //  private def dateToIsoString(date: Date) =
  //    date match {
  //      case null => localIsoDateFormatter.get().format(new Date())
  //      case _    => localIsoDateFormatter.get().format(date)
  //    }
  //
  //  private def parseIsoDateString(date: String): Option[Date] =
  //    Try {
  //      date match {
  //        case null => new Date()
  //        case _    => localIsoDateFormatter.get().parse(date)
  //      }
  //    }.toOption
  implicit object AnyJsonFormat extends JsonFormat[Any] {
    override def write(x: Any) = x match {
      case n: Int                   => JsNumber(n)
      case s: String                => JsString(s)
      case b: Boolean if b == true  => JsTrue
      case b: Boolean if b == false => JsFalse
    }
    override def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case JsTrue      => true
      case JsFalse     => false
    }
  }
}
