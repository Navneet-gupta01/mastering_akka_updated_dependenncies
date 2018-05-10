package com.navneetgupta.bookstore.book

import com.navneetgupta.bookstore.common.BookStoreActor
import akka.actor.Props
import com.navneetgupta.bookstore.common.ErrorMessage
import com.navneetgupta.bookstore.domain.book._
import scala.concurrent.Future
import java.util.Date
import com.navneetgupta.bookstore.common.Failure
import com.navneetgupta.bookstore.common.FailureType

object BookManager {
  val Name = "book-manager"
  val props = Props[BookManager]
  class TagExistsException extends Exception
  val TagAlreadyExistsError = ErrorMessage("book.tag.exists", Some("The tag supplied already exists on the book supplied"))
}

class BookManager extends BookStoreActor {
  import context.dispatcher
  import BookManager._

  val dao = new BookManagerDao

  override def receive = {
    case FindBook(id) =>
      pipeResponse(dao.findBookById(id))

    case FindBooksForId(ids) =>
      pipeResponse(dao.findBookByIds(ids))

    case FindBooksByTags(tags) =>
      val idsFut = dao.findBookIdsByTags(tags)
      val result = for {
        ids <- idsFut
        books <- lookupBooksByIds(ids)
      } yield books
      pipeResponse(result)

    //case FindBooksByTitle(title) =>
    case FindBookByAuthor(author) =>
      pipeResponse(dao.findBooksByAuthor(author))
    case CreateBook(title, author, tags, cost) =>
      val book = Book(0, title, author, cost, 0, tags, new Date, new Date)
      pipeResponse(dao.createBook(book))

    case AddTagToBook(bookId, tag) =>
      val result =
        manipulateTags(bookId, tag) { (book, tag) =>
          if (book.tags.contains(tag)) Future.failed(new TagExistsException)
          else dao.tagBook(book, tag)
        }.
          recover {
            case ex: TagExistsException => Failure(FailureType.Validation, TagAlreadyExistsError)
          }
      pipeResponse(result)

    case RemoveTagFromBook(bookId, tag) =>
      val result = manipulateTags(bookId, tag)(dao.untagBook)
      pipeResponse(result)

    case AddInventoryToBook(bookId, amount) =>
      val result =
        for {
          book <- dao.findBookById(bookId)
          addRes <- checkExistsAndThen(book)(b => dao.addInventoryToBook(b, amount))
        } yield addRes
      pipeResponse(result)
    case DeleteBook(bookId) =>
      val result =
        for {
          book <- dao.findBookById(bookId)
          addRes <- checkExistsAndThen(book)(dao.deleteBook)
        } yield addRes
      pipeResponse(result)
  }

  def lookupBooksByIds(ids: Seq[Int]) =
    if (ids.isEmpty) Future.successful(Vector.empty)
    else dao.findBookByIds(ids)

  def manipulateTags(bookId: Int, tag: String)(f: (Book, String) => Future[Book]): Future[Option[Book]] = {
    for {
      book <- dao.findBookById(bookId)
      tagRes <- checkExistsAndThen(book)(b => f(b, tag))
    } yield tagRes
  }

  def checkExistsAndThen(book: Option[Book])(f: Book => Future[Book]): Future[Option[Book]] = {
    book.fold(Future.successful(book))(b => f(b).map(Some(_)))
  }
}
