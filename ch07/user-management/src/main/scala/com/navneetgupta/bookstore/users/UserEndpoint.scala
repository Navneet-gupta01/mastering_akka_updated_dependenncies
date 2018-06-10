package com.navneetgupta.bookstore.users

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.users.CustomerRelationsManager._
import com.navneetgupta.bookstore.users.User.UserInput
import com.navneetgupta.bookstore.users.UserView.FindUsersByName
import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.common.BookstoreRoutesDefinition
import akka.http.scaladsl.server.Route
import com.navneetgupta.bookstore.users.User.Command.UpdatePersonalInfo
import com.navneetgupta.bookstore.users.UserViewBuilder.UserRM
import akka.stream.Materializer
import akka.actor.ActorSystem

class UserEndpoint(customerRelationnsManager: ActorRef, view: ActorRef)(implicit val ec: ExecutionContext) extends BookstoreRoutesDefinition with UserJosnProtocol {

  import akka.pattern._
  import akka.http.scaladsl.server.Directives._

  override def routes(implicit system: ActorSystem, ec: ExecutionContext, mater: Materializer): Route = {
    pathPrefix("user") {
      path(Segment) { email =>
        get {
          serviceAndComplete[UserFO](FindUserByEmail(email), customerRelationnsManager)
        } ~
          put {
            entity(as[UserInput]) { input =>
              serviceAndComplete[UserFO](UpdateUser(email, input), customerRelationnsManager)
            }
          } ~
          delete {
            serviceAndComplete[UserFO](DeleteUser(email), customerRelationnsManager)
          }
      } ~
        pathEndOrSingleSlash {
          (get & parameter('name)) { name =>
            serviceAndComplete[List[UserRM]](FindUsersByName(name), view)
          } ~
            post {
              entity(as[CreateUserInput]) { input =>
                serviceAndComplete[UserFO](CreateUser(input), customerRelationnsManager)
              }
            }
        }
    }
  }
}
