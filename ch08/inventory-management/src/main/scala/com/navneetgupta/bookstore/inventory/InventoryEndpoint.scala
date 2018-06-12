package com.navneetgupta.bookstore.inventory

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.BookstoreRoutesDefinition
import akka.stream.Materializer
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json._
import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.inventory.BookViewBuilder.BookRM

class InventoryEndpoint(inventoryClerk: ActorRef, bookView: ActorRef)(implicit val ec: ExecutionContext) extends BookstoreRoutesDefinition with InventoryJsonProtocol {

  import akka.pattern._
  import com.navneetgupta.bookstore.inventory.InventoryClerk._
  import BookView._
  import akka.http.scaladsl.server.Directives._

  override def routes(implicit system: ActorSystem, ec: ExecutionContext, mater: Materializer): Route = {
    pathPrefix("book") {
      get {
        path(Segment) { bookId =>
          serviceAndComplete[BookFO](FindBook(bookId), inventoryClerk)
        } ~ pathEndOrSingleSlash {
          parameter('author) { author =>
            serviceAndComplete[List[BookRM]](FindBooksByAuthor(author), bookView)
          } ~
            parameter('tag.*) { tags =>
              serviceAndComplete[List[BookRM]](FindBooksByTags(tags.toSeq), bookView)
            }
        }
      } ~ (post & pathEndOrSingleSlash) {
        entity(as[CreateBook]) { msg =>
          serviceAndComplete[BookFO](msg, inventoryClerk)
        }
      } ~
        path(Segment / "tag" / Segment) { (bookId, tag) =>
          put {
            serviceAndComplete[BookFO](AddTagToBook(bookId, tag), inventoryClerk)
          } ~
            delete {
              serviceAndComplete[BookFO](RemoveTagFromBook(bookId, tag), inventoryClerk)
            }
        } ~
        pathPrefix(Segment) { bookId =>
          (pathEndOrSingleSlash & delete) {
            serviceAndComplete[BookFO](DeleteBook(bookId), inventoryClerk)
          } ~
            (path("inventory" / IntNumber) & put) { amount =>
              serviceAndComplete[BookFO](AddInventoryToBook(bookId, amount), inventoryClerk)
            }
        }
    }
  }

  /*
   * override def intent = {
    case req @ GET(Path(Seg("api" :: "book" :: bookId :: Nil))) =>
      println("inventoryClerk Actor is {} " + inventoryClerk.getClass.getName + "         " + inventoryClerk.path.name)
      val f = (inventoryClerk ? FindBook(bookId))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(TagParam(tags)) =>
      println("Tags to search is " + tags)
      println("bookView Actor is {} ", bookView.getClass.getName + "            " + bookView.path.name)
      val f = (bookView ? FindBooksByTags(tags))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(AuthorParam(author)) =>
      val f = (bookView ? FindBooksByAuthor(author))
      respond(f, req)

    case req @ POST(Path(Seg("api" :: "book" :: Nil))) =>
      val createBook = parseJson[CreateBook](Body.string(req))
      val f = (inventoryClerk ? createBook)
      respond(f, req)

    case req @ Path(Seg("api" :: "book" :: bookId :: "tag" :: tag :: Nil)) =>
      req match {
        case PUT(_) =>
          respond((inventoryClerk ? AddTagToBook(bookId, tag)), req)
        case DELETE(_) =>
          respond((inventoryClerk ? RemoveTagFromBook(bookId, tag)), req)
        case other =>
          req.respond(Pass)
      }
    case req @ PUT(Path(Seg("api" :: "book" :: bookId :: "inventory" :: amount :: Nil))) =>
      val f = (inventoryClerk ? AddInventoryToBook(bookId, amount.toInt))
      respond(f, req)

    case req @ DELETE(Path(Seg("api" :: "book" :: bookId :: Nil))) =>
      val f = (inventoryClerk ? DeleteBook(bookId))
      respond(f, req)
  }**/
}
