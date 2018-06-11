package com.navneetgupta.bookstore.users

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.users.UserViewBuilder.UserRM
import com.navneetgupta.bookstore.users.CustomerRelationsManager.CreateUserInput
import com.navneetgupta.bookstore.users.User.UserInput
import java.text.SimpleDateFormat
import spray.json._
import scala.util.Try
import java.util.Date
import spray.json.DeserializationException

trait UserJosnProtocol extends BookstoreJsonProtocol {
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

  implicit val userFoFormat = jsonFormat6(UserFO.apply)
  implicit val userRmFormat = jsonFormat5(UserRM.apply)
  implicit val userInputFormat = jsonFormat2(UserInput.apply)
  implicit val createUserInputFormat = jsonFormat3(CreateUserInput.apply)
}
