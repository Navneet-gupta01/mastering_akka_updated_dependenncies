package com.navneetgupta.bookstore.common

import com.typesafe.config.Config
import java.util.Date

trait BookStoreDao {
  //import slick.driver.PostgresDriver.api._
  import slick.jdbc.PostgresProfile.api._

  def db = PostgresDb.db
  object DaoHelpers {
    implicit class EnhancedDate(date: Date) {
      def toSqlDate = new java.sql.Date(date.getTime)
    }
  }

  def lastIdSelect(table: String) = {
    sql"select currval('#${table}_id_seq')".as[Int]
  }
}

object PostgresDb {
  import slick.driver.PostgresDriver.backend._
  private[common] var db: Database = _
  val user = "postgres"
  val url = "jdbc:postgresql://localhost:5432/akkaexampleapp"
  val password = "postgres"
  val driver = "org.postgresql.Driver"
  def init(conf: Config): Unit = {
    db = Database.forURL(url, user = user, password = password, driver = driver)
  }
}
