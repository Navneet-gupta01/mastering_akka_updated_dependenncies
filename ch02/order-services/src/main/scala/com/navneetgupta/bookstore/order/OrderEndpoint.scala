package com.navneetgupta.bookstore.order

import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import com.navneetgupta.bookstore.common.BookStorePlan
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.request._
import com.navneetgupta.bookstore.domain.order.FindOrderById
import com.navneetgupta.bookstore.domain.order.FindOrderForUser
import org.json4s._
import org.json4s.native.JsonMethods._
import com.navneetgupta.bookstore.domain.order.FindOrderForBook
import com.navneetgupta.bookstore.domain.order.FindOrderByBookTag
import com.navneetgupta.bookstore.domain.order.CreateOrder

@Sharable
class OrderEndpoint(orderManager: ActorRef)(implicit override val ec: ExecutionContext) extends BookStorePlan {

  import akka.pattern.ask

  object UserIdParam extends Params.Extract("userId", Params.first ~> Params.int)
  object BookIdParam extends Params.Extract("bookId", Params.first ~> Params.int)
  object BookTagParam extends Params.Extract("bookTag", Params.first ~> Params.nonempty)

  override def intent = {
    case req @ GET(Path(Seg("api" :: "order" :: id :: Nil))) =>
      val f = (orderManager ? FindOrderById(id.toInt))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(UserIdParam(userId)) =>
      val f = (orderManager ? FindOrderForUser(userId))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookIdParam(bookId)) =>
      val f = (orderManager ? FindOrderForBook(bookId))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookTagParam(tag)) =>
      val f = (orderManager ? FindOrderByBookTag(tag))
      respond(f, req)
    case req @ POST(Path(Seg("api" :: "order" :: Nil))) =>
      val createReq = parse(Body.string(req)).extract[CreateOrder]
      val f = (orderManager ? createReq)
      respond(f, req)
  }
}
