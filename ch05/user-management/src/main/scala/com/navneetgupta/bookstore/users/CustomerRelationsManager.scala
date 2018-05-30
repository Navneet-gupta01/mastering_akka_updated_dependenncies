package com.navneetgupta.bookstore.users

import akka.actor.Props
import com.navneetgupta.bookstore.users.User.UserInput
import java.util.Date
import com.navneetgupta.bookstore.common.PersistentEntity.GetState
import akka.util.Timeout
import com.navneetgupta.bookstore.common._
import com.navneetgupta.bookstore.common.PersistentEntity.MarkAsDeleted

object CustomerRelationsManager {
  def props = Props[CustomerRelationsManager]
  val Name = "customer-relations-manager"

  //Lookup Operations
  case class FindUserByEmail(email: String)

  //Modify operations
  case class CreateUserInput(email: String, firstName: String, lastName: String)
  case class CreateUser(input: CreateUserInput)
  case class UpdateUser(email: String, input: UserInput)
  case class DeleteUser(email: String)

  val EmailNotUniqueError = ErrorMessage("user.email.nonunique", Some("The email supplied for a create or update is not unique"))
  //Events
}

class CustomerRelationsManager extends Aggregate[UserFO, User] {

  import CustomerRelationsManager._
  import context.dispatcher
  import com.navneetgupta.bookstore.common.EntityActor._
  import scala.concurrent.duration._
  import akka.pattern.ask
  import User._

  //val repo = new UserRepository

  override def receive = {
    case FindUserByEmail(email) =>
      log.info("Finding User by email {}", email)
      val user = lookupOrCreateChild(email)
      forwardCommand(email, GetState)
    case CreateUser(input) =>
      val user = lookupOrCreateChild(input.email)
      implicit val timeout = Timeout(5 seconds)
      val stateFut = (user ? GetState).mapTo[ServiceResult[UserFO]]
      val caller = sender()
      stateFut onComplete {
        case util.Success(FullResult(user)) =>
          caller ! Failure(FailureType.Validation, EmailNotUniqueError)

        case util.Success(EmptyResult) =>
          val fo = UserFO(input.email, input.firstName, input.lastName, new Date, new Date)
          user.tell(Command.CreateUser(fo), caller)

        case _ =>
          caller ! Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
      }
    case UpdateUser(email, input) =>
      forwardCommand(email, Command.UpdatePersonalInfo(input))

    case DeleteUser(email) =>
      forwardCommand(email, MarkAsDeleted)
  }

  override def entityProps(email: String): akka.actor.Props = User.props(email)
}
