package com.navneetgupta.bookstore.user

import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.BookStoreDao
import slick.jdbc.GetResult
import com.navneetgupta.bookstore.domain.user._

object UserManagerDao {
  val SelectFields = "select id, firstName, lastName, email, createTs, modifyTs from StoreUser "
  implicit val GetUser = GetResult { r => BookStoreUser(r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp) }
}

class UserManagerDao(implicit val ec: ExecutionContext) extends BookStoreDao {
  import UserManagerDao._
  import DaoHelpers._
  import slick.driver.PostgresDriver.api._

  def createUser(user: BookStoreUser) = {
    val insert = sqlu"""
      insert into StoreUser (firstName, lastName, email, createTs, modifyTs)
      values (${user.firstName}, ${user.lastName}, ${user.email}, ${user.createTs.toSqlDate}, ${user.modifyTs.toSqlDate})
    """
    val idget = lastIdSelect("storeuser")
    db.run(insert.andThen(idget).withPinnedSession).map(id => user.copy(id = id.headOption.getOrElse(0)))
  }

  def findUserById(userId: Int) = {
    db.
      run(sql"#$SelectFields where id = $userId and not deleted".as[BookStoreUser]).
      map(_.headOption)
  }

  def findUserByEmail(email: String) = {
    db
      .run(sql"#$SelectFields where email = $email and not deleted".as[BookStoreUser])
      .map(_.headOption)
  }

  def updateUserInfo(id: Int, user: UserInput) = {
    val update = sqlu"""
      update StoreUser set firstName = ${user.firstName},
      lastName = ${user.lastName}, email = ${user.email} where id = ${id}
    """

    db.run(update).map(_ => user)
  }

  def updateBookStoreUserInfo(user: BookStoreUser) = {
    val update = sqlu"""
      update StoreUser set firstName = ${user.firstName},
      lastName = ${user.lastName}, email = ${user.email} where id = ${user.id}
    """
    db.run(update).map(_ => user)
  }

  def deleteUser(user: BookStoreUser) = {
    db.run(sqlu"delete from StoreUser where id = ${user.id}").map(_ => user.copy(deleted = true))
  }
}
