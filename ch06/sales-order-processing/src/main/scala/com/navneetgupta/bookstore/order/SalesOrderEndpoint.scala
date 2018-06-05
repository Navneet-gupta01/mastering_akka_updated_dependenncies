package com.navneetgupta.bookstore.order

import akka.actor.ActorRef
import com.navneetgupta.bookstore.common.BookstorePlan
import scala.concurrent.ExecutionContext
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.request._
import org.json4s._
import org.json4s.native.JsonMethods._

@Sharable
class SalesOrderEndpoint(salesAssociate: ActorRef, view: ActorRef)(implicit override val ec: ExecutionContext) extends BookstorePlan {

  import akka.pattern._
  import SalesAssociate._
  import SalesOrderView._

  object UserIdParam extends Params.Extract("userId", Params.first ~> Params.trimmed)
  object BookIdParam extends Params.Extract("bookId", Params.first ~> Params.trimmed)
  object BookTagParam extends Params.Extract("bookTag", Params.first ~> Params.nonempty)

  override def intent = {
    case req @ GET(Path(Seg("api" :: "order" :: id :: Nil))) =>
      val f = (salesAssociate ? FindOrderById(id))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(UserIdParam(userId)) =>
      val f = (view ? FindOrdersForUser(userId))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookIdParam(bookId)) =>
      val f = (view ? FindOrdersForBook(bookId))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookTagParam(tag)) =>
      val f = (view ? FindOrdersForBookTag(tag))
      respond(f, req)
    case req @ POST(Path(Seg("api" :: "order" :: Nil))) =>
      val createReq = parse(Body.string(req)).extract[CreateNewOrder]
      println("Create Order Request Body is:  " + createReq)
      val f = (salesAssociate ? createReq)
      respond(f, req)
  }
}
