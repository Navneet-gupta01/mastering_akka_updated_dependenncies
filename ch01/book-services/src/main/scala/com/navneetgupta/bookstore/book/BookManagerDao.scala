package com.navneetgupta.bookstore.book

import com.navneetgupta.bookstore.common.BookStoreDao
import scala.concurrent.ExecutionContext
import slick.jdbc.GetResult
import com.navneetgupta.bookstore.domain.book.Book
import com.navneetgupta.bookstore.domain.book.CreateBook
import slick.dbio.DBIOAction

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

  def findBookById(bookId: Int) = {
    findBookByIds(Seq(bookId)).
      map(_.headOption)
  }

  def findBookByIds(bookIds: Seq[Int]) = {
    val bookIdsParam = s"${bookIds.mkString(",")}"
    db.
      run(sql"""#$BookLookupPrefix b.id in (#$bookIdsParam) and not b.deleted group by b.id""".as[Book])
  }

  def findBookIdsByTags(tags: Seq[String]) = {
    val tagsParam = tags.map(t => s"'${t.toLowerCase}'").mkString(",")
    val idsWithAllTags = db.run(sql"select bookId, count(bookId) from BookTag where lower(tag) in (#$tagsParam) group by bookId having count(bookId) = ${tags.size}".as[(Int, Int)])
    idsWithAllTags.map(_.map(_._1))
  }

  def findBooksByAuthor(author: String) = {
    val param = s"%${author.toLowerCase}%"
    db
      .run(sql"""#$BookLookupPrefix lower(b.author) like $param and not b.deleted group by b.id""".as[Book])
  }

  def createBook(book: Book) = {
    val insert =
      sqlu"""
        insert into Book (title, author, cost, inventoryamount, createts)
        values (${book.title}, ${book.author}, ${book.cost}, ${book.inventoryAmount}, ${book.createTs.toSqlDate})
      """
    val idget = lastIdSelect("book")
    def tagsInserts(bookId: Int) = DBIOAction.sequence(book.tags.map(t => sqlu"insert into BookTag (bookid, tag) values ($bookId, $t)"))

    val txn =
      for {
        bookRes <- insert
        id <- idget
        if id.headOption.isDefined
        _ <- tagsInserts(id.head)
      } yield {
        book.copy(id = id.head)
      }

    db.run(txn.transactionally)
  }

  def tagBook(book: Book, tag: String) =
    db.run(sqlu"insert into BookTag values (${book.id}, $tag)").map(_ => book.copy(tags = book.tags :+ tag))

  def untagBook(book: Book, tag: String) = {
    val param = s"${tag.toLowerCase}"
    db.run(sqlu"delete from BookTag where bookId=${book.id} and lower(tag) = $param)").map(_ => book.copy(tags = book.tags.filterNot(_ == tag)))
  }

  def addInventoryToBook(book: Book, amount: Int) =
    db.run(sqlu"update Book set inventoryAmount = inventoryAmount + $amount where id = ${book.id}").
      map(_ => book.copy(inventoryAmount = book.inventoryAmount + amount))

  def deleteBook(book: Book) = {
    val bookDelete = sqlu"update Book set deleted = true where id = ${book.id}"
    db.run(bookDelete).map(_ => book.copy(deleted = true))
  }

}
