package com.navneetgupta.bookstore.books

import com.navneetgupta.bookstore.common.BookStoreDao
import scala.concurrent.ExecutionContext
import slick.jdbc.GetResult
import com.navneetgupta.bookstore.domain.book.Book
import com.navneetgupta.bookstore.domain.book.CreateBook

object BookManagerDao {
  implicit val GetBook = GetResult { r => Book(r.<<, r.<<, r.<<, r.<<, r.<<, r.nextString.split(",").filter(_.nonEmpty).toList, r.nextTimestamp, r.nextTimestamp) }
  val BookLookupPrefix = """
    select b.id, b.title, b.author, b.cost, b.inventoryAmount, array_to_string(array_agg(t.tag), ',') as tags, b.createTs, b.modifyTs
    from Book b left join BookTag t on b.id = t.bookId where
  """
}

class BookManagerDao(implicit val ec: ExecutionContext) extends BookStoreDao {
  import BookManagerDao._
  import DaoHelpers._
  import slick.driver.PostgresDriver.api._

  def createBook(book: CreateBook) = {
    val result = sqlu"""insert into StoreUser (firstName, lastName, email, createTs, modifyTs)
      values (${user.firstName}, ${user.lastName}, ${user.email}, ${user.createTs.toSqlDate}, ${user.modifyTs.toSqlDate})"""
  }

}
