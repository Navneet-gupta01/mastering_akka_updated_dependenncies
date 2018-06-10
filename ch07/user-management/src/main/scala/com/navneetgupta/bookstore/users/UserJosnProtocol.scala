package com.navneetgupta.bookstore.users

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.users.UserViewBuilder.UserRM
import com.navneetgupta.bookstore.users.CustomerRelationsManager.CreateUserInput
import com.navneetgupta.bookstore.users.User.UserInput

trait UserJosnProtocol extends BookstoreJsonProtocol {
  implicit val userFoFormat = jsonFormat6(UserFO.apply)
  implicit val userRmFormat = jsonFormat5(UserRM.apply)
  implicit val userInputFormat = jsonFormat2(UserInput.apply)
  implicit val createUserInputFormat = jsonFormat3(CreateUserInput.apply)
}
