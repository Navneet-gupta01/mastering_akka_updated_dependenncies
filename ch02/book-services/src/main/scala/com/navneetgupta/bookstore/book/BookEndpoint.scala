package com.navneetgupta.bookstore.book

import akka.actor.ActorRef
import com.navneetgupta.bookstore.common.BookStorePlan
import scala.concurrent.ExecutionContext
import io.netty.channel.ChannelHandler.Sharable
import unfiltered.request._
import com.navneetgupta.bookstore.domain.book._
import unfiltered.response._
import org.json4s._
import org.json4s.native.JsonMethods._

@Sharable
class BookEndpoint(bookManager: ActorRef)(implicit override val ec: ExecutionContext) extends BookStorePlan {
  import akka.pattern.ask

  object TagParam extends Params.Extract("tag", { values =>
    val filtered = values.filter(_.nonEmpty)
    if (filtered.isEmpty) None else Some(filtered)
  })

  object AuthorParam extends Params.Extract("author", Params.first ~> Params.nonempty)

  override def intent = {
    case req @ GET(Path(Seg("api" :: "book" :: bookId :: Nil))) =>
      val f = (bookManager ? FindBook(bookId.toInt))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(TagParam(tags)) =>
      val f = (bookManager ? FindBooksByTags(tags))
      respond(f, req)
    case req @ GET(Path(Seg("api" :: "book" :: Nil))) & Params(AuthorParam(author)) =>
      val f = (bookManager ? FindBookByAuthor(author))
      respond(f, req)
    case req @ POST(Path(Seg("api" :: "book" :: Nil))) =>
      val createBook = parse(Body.string(req)).extract[CreateBook] // TODO Verify if it is working
      val f = (bookManager ? createBook)
      respond(f, req)
    case req @ Path(Seg("api" :: "book" :: bookId :: "tag" :: tag :: Nil)) =>
      req match {
        case PUT(_) =>
          respond((bookManager ? AddTagToBook(bookId.toInt, tag)), req)
        case DELETE(_) =>
          respond((bookManager ? RemoveTagFromBook(bookId.toInt, tag)), req)
        case other =>
          req.respond(Pass)
      }
    case req @ PUT(Path(Seg("api" :: "book" :: bookId :: "inventory" :: amount :: Nil))) =>
      val f = (bookManager ? AddInventoryToBook(bookId.toInt, amount.toInt))
      respond(f, req)

    case req @ DELETE(Path(Seg("api" :: "book" :: bookId :: Nil))) =>
      val f = (bookManager ? DeleteBook(bookId.toInt))
      respond(f, req)
  }
}
