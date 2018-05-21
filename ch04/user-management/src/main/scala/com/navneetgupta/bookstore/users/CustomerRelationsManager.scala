package com.navneetgupta.bookstore.users

import com.navneetgupta.bookstore.common.EntityAggregate
import akka.actor.Props
import com.navneetgupta.bookstore.users.User.UserInput
import java.util.Date
import com.navneetgupta.bookstore.users.User.UpdateUserInfo

object CustomerRelationsManager {
  def props = Props[CustomerRelationsManager]
  val Name = "customer-relations-manager"

  //Lookup Operations
  case class FindUserById(id: Int)
  case class FindUserByEmail(email: String)

  //Modify operations
  case class CreateUser(input: UserInput)
  case class UpdateUser(id: Int, input: UserInput)
  case class DeleteUser(userId: Int)

  //Events
}

class CustomerRelationsManager extends EntityAggregate[UserFO, User] {

  import CustomerRelationsManager._
  import context.dispatcher
  import com.navneetgupta.bookstore.common.EntityActor._

  val repo = new UserRepository

  override def receive = {
    case FindUserById(id) =>
      log.info("Finding User {}", id)
      val user = lookupOrCreateChild(id)
      user.forward(GetFieldsObject)
    case FindUserByEmail(email) =>
      log.info("Finding User by email {}", email)
      val result =
        for {
          id <- repo.findUserIdByEmail(email)
          user = lookupOrCreateChild(id.getOrElse(0))
          vo <- askForFo(user)
        } yield vo
      pipeResponse(result)
    case CreateUser(input) =>
      val vo = UserFO(0, input.firstName, input.lastName, input.email, new Date, new Date)
      persistOperation(vo.id, vo)
    case UpdateUser(id, input) =>
      persistOperation(id, UpdateUserInfo(input))
    case DeleteUser(userId) =>
      persistOperation(userId, Delete)
  }

  override def entityProps(id: Int): akka.actor.Props = User.props(id)
}
