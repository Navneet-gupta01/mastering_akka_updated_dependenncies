package com.navneetgupta.bookstore.users

import java.util.Date
import com.navneetgupta.bookstore.common.ReadModelObject
import akka.actor.Props
import com.navneetgupta.bookstore.common.ViewBuilder
import com.navneetgupta.bookstore.common.BookstoreActor
import com.navneetgupta.bookstore.common.ElasticsearchSupport
import akka.persistence.query.Offset

trait UserReadModel {
  def indexRoot = "users"
  def entityType = User.EntityType
}

object UserViewBuilder {
  val Name = "user-view-builder"
  case class UserRM(email: String, firstName: String, lastName: String,
                    createTs: Date, deleted: Boolean = false) extends ReadModelObject {
    override def id = email
  }
  def props = Props[UserViewBuilder]
}

class UserViewBuilder extends ViewBuilder[UserViewBuilder.UserRM] with UserReadModel {
  import User.Event._
  import ViewBuilder._
  import UserViewBuilder._

  def projectionId = Name

  def actionFor(id: String, offset: Offset, event: Any) = event match {
    case UserCreated(user) =>
      val rm = UserRM(user.email, user.firstName, user.lastName, user.createTs, user.deleted)
      InsertAction(id, rm)

    case PersonalInfoUpdated(first, last) =>
      UpdateAction(id, List("firstName = fn", "lastName = ln"), Map("fn" -> first, "ln" -> last))

    case UserDeleted(email) =>
      UpdateAction(id, "deleted = true", Map.empty[String, Any])
  }
}

object UserView {
  val Name = "user-view"
  case class FindUsersByName(name: String)
  def props = Props[UserView]
}

class UserView extends BookstoreActor with ElasticsearchSupport with UserReadModel {
  import UserView._
  import context.dispatcher

  def receive = {
    case FindUsersByName(name) =>
      val results = queryElasticsearch(s"firstName:$name OR lastName:$name")
      pipeResponse(results)
  }
}
