package com.navneetgupta.bookstore.common

import java.util.Date
import akka.actor.ActorSystem
import com.typesafe.config.Config
import scala.concurrent.Future
import slick.jdbc.PostgresProfile

trait BooksRepository {
  import slick.driver.PostgresDriver.api._

  def db = PostgresDb.db

  /**
   * Defines some helpers to use in daos
   */
  object DaoHelpers {

    /**
     * Adds a method to easily convert from util.Date to sql.Date
     */
    implicit class EnhancedDate(date: Date) {

      /**
       * Converts from the date suplied in the constructor into a sql.Date
       * @return a sql.Date
       */
      def toSqlDate = new java.sql.Date(date.getTime)
    }
  }

  /**
   * Gets a select statement to use to select the last id val for a serial id field in Postgres
   * @param table The name of the table to get the last id from
   * @return a DBIOAction used to select the last id val
   */
  def lastIdSelect(table: String) = sql"select currval('#${table}_id_seq')".as[Int]
}

trait EntityRepository[FO <: EntityFieldsObject[FO]] extends BooksRepository {
  /**
   * Load the entity from the repo
   * @param id The id of the entity
   * @return a Future wrapping an optional fields object
   */
  def loadEntity(id: Int): Future[Option[FO]]

  /**
   * Save the entity to the repo
   * @param vo The fields object representation of the entity
   * @return a Future wrapping the number of rows updated
   */
  def persistEntity(fo: FO): Future[Int]

  /**
   * Delete the entity from the repo
   * @param id The id of the entity to delete
   * @return a Future wrapping the number of rows updated
   */
  def deleteEntity(id: Int): Future[Int]
}
object PostgresDb {
  import slick.driver.PostgresDriver.backend._
  private[common] var db: Database = _

  //  def init(conf: Config): Unit = {
  //    db = Database.forConfig("psqldb", conf)
  //  }
  val user = "postgres"
  val url = "jdbc:postgresql://localhost:5432/akkaexampleapp"
  val password = "postgres"
  val driver = "org.postgresql.Driver"
  def init(conf: Config): Unit = {
    db = Database.forURL(url, user = user, password = password, driver = driver)
  }
}
