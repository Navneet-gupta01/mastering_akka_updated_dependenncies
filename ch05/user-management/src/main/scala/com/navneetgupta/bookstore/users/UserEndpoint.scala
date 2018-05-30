package com.navneetgupta.bookstore.users

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.BookstorePlan
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.request._
import org.json4s._
import org.json4s.native.JsonMethods._
import com.navneetgupta.bookstore.users.CustomerRelationsManager._
import com.navneetgupta.bookstore.users.User.UserInput
import com.navneetgupta.bookstore.users.UserView.FindUsersByName

@Sharable
class UserEndpoint(customerRelationnsManager: ActorRef, view: ActorRef)(implicit override val ec: ExecutionContext) extends BookstorePlan {

  import akka.pattern._

  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty)
  object NameParam extends Params.Extract("name", Params.first ~> Params.nonempty)

  override def intent = {
    case req @ GET(Path(Seg("api" :: "user" :: Nil))) & Params(EmailParam(email)) =>
      val f = (customerRelationnsManager ? FindUserByEmail(email))
      respond(f, req)

    case req @ GET(Path(Seg("api" :: "user" :: Nil))) & Params(NameParam(name)) =>
      val f = (view ? FindUsersByName(name))
      respond(f, req)
    case req @ POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = parse(Body.string(req)).extract[CreateUserInput]
      val f = (customerRelationnsManager ? CreateUser(input))
      respond(f, req)
    case req @ PUT(Path(Seg("api" :: "user" :: email :: Nil))) =>
      val input = parse(Body.string(req)).extract[UserInput]
      val f = (customerRelationnsManager ? UpdateUser(email, input))
      respond(f, req)

    case req @ DELETE(Path(Seg("api" :: "user" :: email :: Nil))) =>
      val f = (customerRelationnsManager ? DeleteUser(email))
      respond(f, req)
  }
}
