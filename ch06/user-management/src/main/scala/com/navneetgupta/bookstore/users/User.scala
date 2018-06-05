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
import com.navneetgupta.bookstore.common.EntityEvent
import com.navneetgupta.bookstore.user.Datamodel
import com.navneetgupta.bookstore.common.DatamodelReader
import com.navneetgupta.bookstore.common.PersistentEntity
import com.navneetgupta.bookstore.user.Datamodel.UserCreated

object UserFO {
  def empty = new UserFO("", "", "", new Date(0), new Date(0))
}

case class UserFO(email: String, firstName: String, lastName: String,
                  createTs: Date, modifyTs: Date, deleted: Boolean = false) extends EntityFieldsObject[String, UserFO] {
  def assignId(id: String) = this.copy(email = id)
  def id = email
  def markDeleted = this.copy(deleted = true)
}

object User {
  val EntityType = "user"
  case class UserInput(firstName: String, lastName: String)

  object Command {
    case class CreateUser(user: UserFO)
    case class UpdatePersonalInfo(input: UserInput)
  }

  object Event {
    trait UserEvent extends EntityEvent { def entityType = EntityType }
    case class UserCreated(user: UserFO) extends UserEvent {
      def toDatamodel = {
        println("To DataModel UserCreated")
        val userDm = Datamodel.BookstoreUser.newBuilder().
          setEmail(user.email).
          setFirstName(user.firstName).
          setLastName(user.lastName).
          setCreateTs(new Date().getTime).
          setModifyTs(new Date().getTime).
          setDeleted(user.deleted)
          .build

        Datamodel.UserCreated.newBuilder().
          setUser(userDm).
          build
      }
    }
    object UserCreated extends DatamodelReader {
      def fromDatamodel = {

        case dm: Datamodel.UserCreated =>
          println("From DataModel UserCreated")
          val user = dm.getUser()
          UserCreated(UserFO(user.getEmail(), user.getFirstName(), user.getLastName(), new Date(user.getCreateTs()), new Date(user.getModifyTs()), user.getDeleted()))
      }
    }

    case class PersonalInfoUpdated(firstName: String, lastName: String) extends UserEvent {
      def toDatamodel = {
        println("To DataModel PersonalInfoUpdated")
        Datamodel.PersonalInfoUpdated.newBuilder().
          setFirstName(firstName).
          setLastName(lastName).
          setModifyTs(new Date().getTime).
          build
      }
    }

    object PersonalInfoUpdated extends DatamodelReader {
      def fromDatamodel = {
        case dm: Datamodel.PersonalInfoUpdated =>
          println("From DataModel PersonalInfoUpdated")
          PersonalInfoUpdated(dm.getFirstName(), dm.getLastName())
      }
    }

    case class UserDeleted(email: String) extends UserEvent {
      def toDatamodel = {
        println("To DataModel UserDeleted")
        Datamodel.UserDeleted.newBuilder().
          setEmail(email).
          setModifyTs(new Date().getTime). // Not needed since events are alreay there for the time at which they are called
          build
      }
    }

    object UserDeleted extends DatamodelReader {
      def fromDatamodel = {
        case dm: Datamodel.UserDeleted =>
          println("From DataModel UserDeleted")
          UserDeleted(dm.getEmail())
      }
    }
  }

  def props(id: String) = Props(classOf[User], id)
}

class User(email: String) extends PersistentEntity[UserFO](email) {

  import User._
  import context.dispatcher
  import EntityActor._
  import akka.pattern._

  def initialState = UserFO.empty

  override def snapshotAfterCount: Option[Int] = Some(5)

  def isCreateMessage(cmd: Any): Boolean = cmd match {
    case co: Command.CreateUser => true
    case _                      => false
  }

  override def newDeleteEvent: Option[EntityEvent] = Some(Event.UserDeleted(email))

  override def additionalCommandHandling: Receive = {
    case Command.CreateUser(user) =>
      persist(Event.UserCreated(user)) { handleEventAndRespond() }
    case Command.UpdatePersonalInfo(input) =>
      persist(Event.PersonalInfoUpdated(input.firstName, input.lastName)) { handleEventAndRespond() }
  }

  def handleEvent(event: EntityEvent) = event match {
    case Event.UserCreated(userFO) =>
      state = userFO
    case Event.PersonalInfoUpdated(firstName: String, lastName: String) =>
      state = state.copy(firstName = firstName, lastName = lastName)
    case Event.UserDeleted(email: String) =>
      state = state.markDeleted
  }
}
