package com.navneetgupta.bookstore.inventory

import slick.jdbc.GetResult
import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.EntityRepository
import scala.concurrent.Future

object BookRepository {
  implicit val GetBook = GetResult { r => BookFO(r.<<, r.<<, r.<<, r.nextString.split(",").filter(_.nonEmpty).toList, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp) }
  class InventoryNotAvailableException extends Exception
}
class BookRepository(implicit ec: ExecutionContext) extends EntityRepository[BookFO] {
  import slick.driver.PostgresDriver.api._
  import BookRepository._
  import DaoHelpers._
  import slick.dbio._

  def tagBook(id: Int, tag: String) = {
    db.run(sqlu"insert into BookTag values ($id, $tag)")
  }
  def untagBook(id: Int, tag: String) = {
    db.run(sqlu"delete from BookTag where bookId =  $id and tag = $tag")
  }

  def addInventoryToBook(id: Int, amount: Int) = {
    db.run(sqlu"update Book set inventoryAmount = inventoryAmount + $amount where id = ${id}")
  }
  def allocateInventory(id: Int, amount: Int) = {
    db.run(sqlu"update Book set inventoryAmount = inventoryAmount - $amount where id = ${id} and inventoryAmount >= $amount")
  }

  override def loadEntity(id: Int) = {
    println("Loading Entity for id {}", id)
    val query = sql"""
      select b.id, b.title, b.author, array_to_string(array_agg(t.tag), ',') as tags, b.cost, b.inventoryAmount, b.createTs, b.modifyTs
      from Book b left join BookTag t on b.id = t.bookId where id = $id and not b.deleted group by b.id
    """
    db.run(query.as[BookFO].map(_.headOption))
  }

  override def persistEntity(book: BookFO): Future[Int] = {
    println("Persisting Entity for")
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
        id.head
      }

    db.run(txn.transactionally)
  }

  override def deleteEntity(id: Int): Future[Int] = {
    println("Deleteing Entity for id {}", id)
    val bookDelete = sqlu"update Book set deleted = true where id = ${id}"
    db.run(bookDelete).map(_ => id)
  }

  def findBookIdsByTags(tags: Seq[String]) = {
    println("Loading Book Entity for tags {}", tags)
    val tagsParam = tags.map(t => s"'${t.toLowerCase}'").mkString(",")
    val idsWithAllTags = db.run(sql"select bookId, count(bookId) from BookTag where lower(tag) in (#$tagsParam) and not deleted group by bookId having count(bookId) = ${tags.size}".as[(Int, Int)])
    idsWithAllTags.map(_.map(_._1))
  }

  def findBookIdsByAuthor(author: String) = {
    println("Loading Book Entity for author {}", author)
    val param = s"%${author.toLowerCase}%"
    db
      .run(sql"""select id from Book where lower(author) like $param and not deleted""".as[Int])
  }

  def findBookIdsByTitle(title: String) = {
    val param = s"%${title.toLowerCase}%"
    println("Loading Book Entity for title {}", title)
    db
      .run(sql"""select id from Book where lower(title) like $param and not deleted""".as[Int])
  }

  //  def createBook(book: Book) = {
  //
  //  }

}
