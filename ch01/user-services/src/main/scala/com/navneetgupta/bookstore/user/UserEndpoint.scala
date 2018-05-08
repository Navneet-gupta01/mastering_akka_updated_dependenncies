package com.navneetgupta.bookstore.user

import akka.actor.ActorRef
import com.navneetgupta.bookstore.common.BookStorePlan
import scala.concurrent.ExecutionContext
import unfiltered.request.Params
import unfiltered.request._
import com.navneetgupta.bookstore.domain.user._

class UserEndpoint(userManager: ActorRef)(implicit override val ec: ExecutionContext) extends BookStorePlan {

  import akka.pattern.ask

  object EmailParam extends Params.Extract("email", Params.first ~> Params.nonempty)

  override def intent = {
    case req @ GET(Path(Seg("api" :: "user" :: userId :: Nil))) =>
      val f = (userManager ? FindUserById(userId.toInt))
      respond(f, req)
  }
}
