package com.navneetgupta.bookstore.order

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.BookstoreRoutesDefinition
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.server.Route
import com.navneetgupta.bookstore.order.SalesOrderViewBuilder.SalesOrderRM
import com.navneetgupta.bookstore.order.SalesOrder.Command.CreateOrder

class SalesOrderEndpoint(salesAssociate: ActorRef, view: ActorRef)(implicit val ec: ExecutionContext) extends BookstoreRoutesDefinition with SalesOrderJsonProtocol {

  import akka.pattern._
  import SalesAssociate._
  import SalesOrderView._
  import akka.http.scaladsl.server.Directives._

  override def routes(implicit system: ActorSystem, ec: ExecutionContext, mater: Materializer): Route = {
    pathPrefix("order") {
      get {
        path(Segment) { orderId =>
          serviceAndComplete[SalesOrderFO](FindOrderById(orderId), salesAssociate)
        } ~ pathEndOrSingleSlash {
          parameter('email) { email =>
            serviceAndComplete[List[SalesOrderRM]](FindOrdersForUser(email), view)
          } ~ parameter('bookId) { bookId =>
            serviceAndComplete[List[SalesOrderRM]](FindOrdersForBook(bookId), view)
          } ~ parameter('tag) { tag =>
            serviceAndComplete[List[SalesOrderRM]](FindOrdersForBookTag(tag), view)
          }
        }
      } ~ (post & pathEndOrSingleSlash) {
        entity(as[CreateNewOrder]) { msg =>
          serviceAndComplete[SalesOrderFO](msg, salesAssociate)
        }
      }
    }
  }

  //  object UserIdParam extends Params.Extract("userId", Params.first ~> Params.trimmed)
  //  object BookIdParam extends Params.Extract("bookId", Params.first ~> Params.trimmed)
  //  object BookTagParam extends Params.Extract("bookTag", Params.first ~> Params.nonempty)
  //
  //  override def intent = {
  //    case req @ GET(Path(Seg("api" :: "order" :: id :: Nil))) =>
  //      val f = (salesAssociate ? FindOrderById(id))
  //      respond(f, req)
  //    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(UserIdParam(userId)) =>
  //      val f = (view ? FindOrdersForUser(userId))
  //      respond(f, req)
  //    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookIdParam(bookId)) =>
  //      val f = (view ? FindOrdersForBook(bookId))
  //      respond(f, req)
  //    case req @ GET(Path(Seg("api" :: "order" :: Nil))) & Params(BookTagParam(tag)) =>
  //      val f = (view ? FindOrdersForBookTag(tag))
  //      respond(f, req)
  //    case req @ POST(Path(Seg("api" :: "order" :: Nil))) =>
  //      val createReq = parse(Body.string(req)).extract[CreateNewOrder]
  //      println("Create Order Request Body is:  " + createReq)
  //      val f = (salesAssociate ? createReq)
  //      respond(f, req)
  //  }
}
