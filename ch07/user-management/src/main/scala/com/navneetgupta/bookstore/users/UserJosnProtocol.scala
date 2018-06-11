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
  implicit val userFoFormat = jsonFormat6(UserFO.apply)
  implicit val userRmFormat = jsonFormat5(UserRM.apply)
  implicit val userInputFormat = jsonFormat2(UserInput.apply)
  implicit val createUserInputFormat = jsonFormat3(CreateUserInput.apply)
}
