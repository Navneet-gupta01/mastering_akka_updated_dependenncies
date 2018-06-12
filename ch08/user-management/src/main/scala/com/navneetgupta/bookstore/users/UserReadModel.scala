package com.navneetgupta.bookstore.users

import java.util.Date
import com.navneetgupta.bookstore.common.ReadModelObject
import akka.actor.Props
import com.navneetgupta.bookstore.common.ViewBuilder
import com.navneetgupta.bookstore.common.BookstoreActor
import com.navneetgupta.bookstore.common.ElasticsearchSupport
import akka.persistence.query.Offset
import akka.persistence.query.EventEnvelope
import akka.stream.ActorMaterializer
import com.navneetgupta.bookstore.users.UserViewBuilder.UserRM

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

class UserViewBuilder extends ViewBuilder[UserViewBuilder.UserRM] with UserReadModel with UserJosnProtocol {
  import User.Event._
  import ViewBuilder._
  import UserViewBuilder._

  implicit val rmFormats = userRmFormat

  def projectionId = Name

  def actionFor(id: String, env: EventEnvelope) = env.event match {
    case UserCreated(user) =>
      val rm = UserRM(user.email, user.firstName, user.lastName, user.createTs, user.deleted)
      InsertAction(id, rm)

    case PersonalInfoUpdated(first, last) =>
      UpdateAction(id, List("firstName = params.fn", "lastName = params.ln"), Map("fn" -> first, "ln" -> last))

    case UserDeleted(email) =>
      UpdateAction(id, "deleted = true", Map.empty[String, Any])
  }
}

object UserView {
  val Name = "user-view"
  case class FindUsersByName(name: String)
  def props = Props[UserView]
}

class UserView extends BookstoreActor with ElasticsearchSupport with UserReadModel with UserJosnProtocol {
  import UserView._
  import context.dispatcher

  implicit val mater = ActorMaterializer()

  def receive = {
    case FindUsersByName(name) =>
      val results = queryElasticsearch[UserRM](s"firstName:$name OR lastName:$name")
      pipeResponse(results)
  }
}
