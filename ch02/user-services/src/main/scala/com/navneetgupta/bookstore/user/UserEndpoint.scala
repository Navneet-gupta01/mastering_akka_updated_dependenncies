package com.navneetgupta.bookstore.user

import akka.actor.ActorRef
import com.navneetgupta.bookstore.common.BookStorePlan
import scala.concurrent.ExecutionContext
import unfiltered.request.Params
import unfiltered.request._
import com.navneetgupta.bookstore.domain.user._
import org.json4s._
import org.json4s.native.JsonMethods._
import io.netty.channel.ChannelHandler.Sharable

@Sharable
class UserEndpoint(userManager: ActorRef)(implicit override val ec: ExecutionContext) extends BookStorePlan {

  import akka.pattern.ask

  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty)

  override def intent = {
    case req @ GET(Path(Seg("api" :: "user" :: userId :: Nil))) =>
      val f = (userManager ? FindUserById(userId.toInt))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "user" :: Nil))) & Params(EmailParam(email)) =>
      val f = (userManager ? FindUserByEmail(email))
      respond(f, req)
    case req @ POST(Path(Seg("api" :: "user" :: Nil))) =>
      val input = parse(Body.string(req)).extract[UserInput]
      val f = (userManager ? CreateUser(input))
      respond(f, req)
    case req @ PUT(Path(Seg("api" :: "user" :: userId :: Nil))) =>
      val input = parse(Body.string(req)).extract[UserInput]
      val f = (userManager ? UpdateUserInfo(userId.toInt, input))
      respond(f, req)

    case req @ DELETE(Path(Seg("api" :: "user" :: userId :: Nil))) =>
      val f = (userManager ? DeleteUser(userId.toInt))
      respond(f, req)
  }
}
