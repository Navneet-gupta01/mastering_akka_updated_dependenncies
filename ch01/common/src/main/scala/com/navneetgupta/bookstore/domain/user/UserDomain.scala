package com.navneetgupta.bookstore.domain.user

import java.util.Date

case class BookStoreUser(id: Int, firstName: String, lastName: String, email: String, createTs: Date, modifyTs: Date)

case class FindUserById(userId: Int)
case class FindUserByEmail(email: String)

case class UserInput(firstName: String, lastName: String, email: String)
case class CreateUser(input: UserInput)
case class UpdateUserInfo(userId: Int, input: UserInput)
case class DeleteUser(userId: Int)
