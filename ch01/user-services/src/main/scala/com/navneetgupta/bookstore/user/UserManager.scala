package com.navneetgupta.bookstore.user

import akka.actor.Props
import com.navneetgupta.bookstore.common.BookStoreActor
import com.navneetgupta.bookstore.common.ErrorMessage
import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.ServiceResult
import com.navneetgupta.bookstore.common.Failure
import com.navneetgupta.bookstore.common.FailureType
import com.navneetgupta.bookstore.domain.user._
import java.util.Date
import scala.concurrent.Future

object UserManager {
  val Name = "user-manager"

  def props = Props[UserManager]
  class EmailNotUniqueException extends Exception
  val EmailNotUniqueError = ErrorMessage("user.email.nonunique", Some("The email supplied for a create or update is not unique"))
}
class UserManager extends BookStoreActor {
  import context.dispatcher
  import UserManager._
  val dao = new UserManagerDao

  val recoverEmailCheck: PartialFunction[Throwable, ServiceResult[_]] = {
    case ex: EmailNotUniqueException =>
      Failure(FailureType.Validation, EmailNotUniqueError)
  }

  def receive = {
    case FindUserById(id) =>
      pipeResponse(dao.findUserById(id))
    case FindUserByEmail(email) =>
      pipeResponse(dao.findUserByEmail(email))
    case CreateUser(UserInput(first, last, email)) =>
      val result =
        for {
          _ <- emailUnique(email)
          daoRes <- dao.createUser(BookStoreUser(0, first, last, email, new Date, new Date))
        } yield daoRes
      pipeResponse(result.recover(recoverEmailCheck))

    case upd @ UpdateUserInfo(id, input) =>
      val result =
        for {
          _ <- emailUnique(input.email, Some(id))
          userOpt <- dao.findUserById(id)
          updated <- maybeUpdate(upd, userOpt)
        } yield updated
      pipeResponse(result.recover(recoverEmailCheck))

    case DeleteUser(userId: Int) =>
      val result =
        for {
          userOpt <- dao.findUserById(userId)
          res <- userOpt.fold[Future[Option[BookStoreUser]]](Future.successful(None)) { u =>
            dao.deleteUser(u).map(Some.apply)
          }
        } yield res
      pipeResponse(result)
  }

  def emailUnique(email: String, existingId: Option[Int] = None) = {
    dao.
      findUserByEmail(email).
      flatMap {
        case None                                      => Future.successful(true)
        case Some(user) if Some(user.id) == existingId => Future.successful(true)
        case _                                         => Future.failed(new EmailNotUniqueException)
      }
  }

  def maybeUpdate(upd: UpdateUserInfo, userOpt: Option[BookStoreUser]) = {
    userOpt.
      map { u =>
        val updated = u.copy(firstName = upd.input.firstName, lastName = upd.input.lastName, email = upd.input.email)
        dao.updateBookStoreUserInfo(updated).map(Some.apply)
      }.
      getOrElse(Future.successful(None))
  }

}
