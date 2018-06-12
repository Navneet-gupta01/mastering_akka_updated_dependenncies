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
import java.util.UUID

abstract class ResumableProjection(identifier: String) {
  def storeLatestOffset(uuid: UUID): Future[Boolean]
  def fetchLatestOffset: Future[Option[Offset]]
}

object ResumableProjection {
  def apply(identifier: String, system: ActorSystem) =
    new CassandraResumableProjection(identifier, system)
}

class CassandraResumableProjection(identifier: String, system: ActorSystem)
    extends ResumableProjection(identifier) {
  val projectionStorage = CassandraProjectionStorage(system) //TODO

  override def storeLatestOffset(uuid: UUID): Future[Boolean] = {
    projectionStorage.updateOffset(identifier, uuid)
    //    offset match {
    //      case NoOffset =>
    //        projectionStorage.updateOffset(identifier, )
    //      case TimeBasedUUID(x) =>
    //
    //    }

  }
  override def fetchLatestOffset: Future[Option[Offset]] = {
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
        identifier varchar primary key, offset uuid)
  """

  val init: Session => Future[Unit] = (session: Session) => for {
    _ <- session.executeAsync(createKeyspaceStmt)
    _ <- session.executeAsync(createTableStmt)
  } yield ()

  val session = new CassandraSession(system, cassandraConfig, init)

  def updateOffset(identifier: String, offset: UUID): Future[Boolean] = (for {
    session <- session.underlying()
    _ <- session.executeAsync(s"update bookstore.projectionoffsets set offset = $offset where identifier = '$identifier'")
  } yield true) recover { case t => false }

  def fetchLatestOffset(identifier: String): Future[Option[Offset]] = for {
    session <- session.underlying()
    rs <- session.executeAsync(s"select offset from bookstore.projectionoffsets where identifier = '$identifier'")
  } yield {
    import collection.JavaConversions._
    rs.all().headOption.map(x => TimeBasedUUID(x.getUUID(0)))
  }
}

object CassandraProjectionStorage extends ExtensionId[CassandraProjectionStorageExt] with ExtensionIdProvider {
  override def lookup = CassandraProjectionStorage
  override def createExtension(system: ExtendedActorSystem) =
    new CassandraProjectionStorageExt(system)
}
