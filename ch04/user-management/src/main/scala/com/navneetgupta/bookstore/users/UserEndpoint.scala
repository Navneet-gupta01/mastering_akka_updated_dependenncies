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

@Sharable
class UserEndpoint(customerRelationnsManager: ActorRef)(implicit override val ec: ExecutionContext) extends BookstorePlan {

  import akka.pattern._

  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty)

  override def intent = {
    case req @ GET(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = (customerRelationnsManager ? FindUserById(userId))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "user" :: Nil))) & Params(EmailParam(email)) =>
      val f = (customerRelationnsManager ? FindUserByEmail(email))
      respond(f, req)
    case req @ POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = parse(Body.string(req)).extract[UserInput]
      val f = (customerRelationnsManager ? CreateUser(input))
      respond(f, req)
    case req @ PUT(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val input = parse(Body.string(req)).extract[UserInput]
      val f = (customerRelationnsManager ? UpdateUser(userId, input))
      respond(f, req)

    case req @ DELETE(Path(Seg("api" :: "user" :: IntPathElement(userId) :: Nil))) =>
      val f = (customerRelationnsManager ? DeleteUser(userId))
      respond(f, req)
  }
}
