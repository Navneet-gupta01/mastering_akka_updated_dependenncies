package com.navneetgupta.bookstore.common

import akka.actor.ActorSystem
import scala.concurrent.Future
import akka.actor.Extension
import akka.event.Logging
import com.datastax.driver.core._
import java.util.concurrent.atomic.AtomicBoolean
import akka.actor.ExtensionIdProvider
import akka.actor.ExtensionId
import akka.actor.ExtendedActorSystem
import akka.persistence.query.Offset
import akka.persistence.query.Sequence
import akka.persistence.query.NoOffset
import akka.persistence.query.TimeBasedUUID
import java.util.Date

abstract class ResumableProjection(identifier: String) {
  def storeLatestOffset(offset: Offset): Future[Boolean]
  def fetchLatestOffset: Future[Offset]
}

object ResumableProjection {
  def apply(identifier: String, system: ActorSystem) =
    new CassandraResumableProjection(identifier, system)
}

class CassandraResumableProjection(identifier: String, system: ActorSystem)
    extends ResumableProjection(identifier) {
  val projectionStorage = CassandraProjectionStorage(system) //TODO

  override def storeLatestOffset(offset: Offset): Future[Boolean] = {
    offset match {
      case NoOffset =>
        projectionStorage.updateOffset(identifier, 1)
      case TimeBasedUUID(x) =>
        projectionStorage.updateOffset(identifier, new Date().getTime)
    }

  }
  override def fetchLatestOffset: Future[Offset] = {
    projectionStorage.fetchLatestOffset(identifier)
  }
}

class CassandraProjectionStorageExt(system: ActorSystem) extends Extension {
  import akka.persistence.cassandra.listenableFutureToFuture
  import system.dispatcher

  val cassandraConfig = system.settings.config.getConfig("cassandra")
  implicit val log = Logging(system.eventStream, "CassandraProjectionStorage")

  var initialized = new AtomicBoolean(false)
  val createKeyspaceStmt = """
      CREATE KEYSPACE IF NOT EXISTS bookstore
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 }
    """

  val createTableStmt = """
      CREATE TABLE IF NOT EXISTS bookstore.projectionoffsets (
        identifier varchar primary key, offset bigint)
  """

  val init: Session => Future[Unit] = (session: Session) => for {
    _ <- session.executeAsync(createKeyspaceStmt)
    _ <- session.executeAsync(createTableStmt)
  } yield ()

  val session = new CassandraSession(system, cassandraConfig, init)

  def updateOffset(identifier: String, offset: Long): Future[Boolean] = (for {
    session <- session.underlying()
    _ <- session.executeAsync(s"update bookstore.projectionoffsets set offset = $offset where identifier = '$identifier'")
  } yield true) recover { case t => false }

  def fetchLatestOffset(identifier: String): Future[Offset] = for {
    session <- session.underlying()
    rs <- session.executeAsync(s"select offset from bookstore.projectionoffsets where identifier = '$identifier'")
  } yield {
    import collection.JavaConversions._
    rs.all().headOption.map(_.getLong(0)) match {
      case None    => NoOffset
      case Some(x) => Sequence(x)
    }
  }
}

object CassandraProjectionStorage extends ExtensionId[CassandraProjectionStorageExt] with ExtensionIdProvider {
  override def lookup = CassandraProjectionStorage
  override def createExtension(system: ExtendedActorSystem) =
    new CassandraProjectionStorageExt(system)
}
