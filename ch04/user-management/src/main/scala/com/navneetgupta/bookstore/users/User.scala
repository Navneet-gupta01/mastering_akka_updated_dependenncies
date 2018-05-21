package com.navneetgupta.bookstore.users

import java.util.Date
import com.navneetgupta.bookstore.common.EntityActor
import com.navneetgupta.bookstore.common.ErrorMessage
import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityActor.ErrorMapper
import com.navneetgupta.bookstore.common.Failure
import com.navneetgupta.bookstore.common.FailureType
import com.navneetgupta.bookstore.common.EntityActor.InitializedData
import scala.concurrent.Future
import com.navneetgupta.bookstore.common.EntityFieldsObject

case class UserFO(id: Int, firstName: String, lastName: String, email: String,
                  createTs: Date, modifyTs: Date, deleted: Boolean = false) extends EntityFieldsObject[Int, UserFO] {
  def assignId(id: Int) = this.copy(id = id)
  def markDeleted = this.copy(deleted = true)
}

object User {

  case class UserInput(firstName: String, lastName: String, email: String)

  case class UpdateUserInfo(input: UserInput)

  def props(id: Int) = Props(classOf[User], id)
  class EmailNotUniqueException extends Exception
  val EmailNotUniqueError = ErrorMessage("user.email.nonunique", Some("The email supplied for a create or update is not unique"))

}

class User(idInput: Int) extends EntityActor[UserFO](idInput) {

  import User._
  import context.dispatcher
  import EntityActor._
  import akka.pattern._

  val repo = new UserRepository
  val errorMapper: ErrorMapper = {
    case ex: EmailNotUniqueException =>
      Failure(FailureType.Validation, EmailNotUniqueError)
  }

  override def customCreateHandling: StateFunction = {
    case Event(vo: UserFO, _) =>
      val checkFut = emailUnique(vo.email)
      checkFut.
        map(b => FinishCreate(vo))
        .to(self, sender())
      stay
  }

  def initializedHandling: StateFunction = {
    case Event(UpdateUserInfo(input), data: InitializedData[UserFO]) =>
      val newFo = data.fo.copy(firstName = input.firstName, lastName = input.lastName, email = input.email)
      val persistFut =
        for {
          _ <- emailUnique(input.email, Some(data.fo.id))
          updated <- repo.updateUserInfo(newFo)
        } yield updated
      requestFoForSender
      persist(data.fo, persistFut, _ => newFo)
  }

  def emailUnique(email: String, existingId: Option[Int] = None) = {
    repo.findUserIdByEmail(email).
      flatMap {
        case None                               => Future.successful(true)
        case Some(id) if Some(id) == existingId => Future.successful(true)
        case _                                  => Future.failed(new EmailNotUniqueException)
      }
  }
}
