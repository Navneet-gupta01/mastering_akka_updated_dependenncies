package com.navneetgupta.bookstore.common

import akka.actor.Stash
import akka.stream.ActorMaterializer
import akka.persistence.query.PersistenceQuery
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.EventEnvelope
import scala.concurrent.Future
import akka.persistence.query.Offset
import java.util.Date
import akka.persistence.query.Sequence
import akka.persistence.query.NoOffset
import akka.persistence.query.TimeBasedUUID
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.ActorMaterializerSettings
import akka.stream.Supervision
import scala.util.control.NonFatal
import akka.stream.scaladsl.Source

trait ReadModelObject extends AnyRef {
  def id: String
}
object ViewBuilder {
  import ElasticsearchApi._
  sealed trait IndexAction
  case class UpdateAction(id: String, expression: List[String],
                          params: Map[String, Any]) extends IndexAction
  object UpdateAction {
    def apply(id: String, expression: String,
              params: Map[String, Any]): UpdateAction =
      UpdateAction(id, List(expression), params)
  }
  case class InsertAction(id: String,
                          rm: ReadModelObject) extends IndexAction
  case class NoAction(id: String) extends IndexAction
  case object DeferredCreate extends IndexAction
  case class LatestOffsetResult(offset: Option[Offset])
  case class EnvelopeAndAction(env: EventEnvelope,
                               action: IndexAction)
  case class EnvelopeAndFunction(env: EventEnvelope,
                                 f: () => Future[IndexingResult])
  case class DeferredCreate(
    flow: Flow[EnvelopeAndAction, EnvelopeAndAction, akka.NotUsed])
      extends IndexAction

}

trait ViewBuilder[RM <: ReadModelObject] extends BookstoreActor with Stash with ElasticsearchUpdateSupport {

  import context.dispatcher
  import ViewBuilder._
  import ElasticsearchApi._
  import akka.pattern.pipe

  //clearIndex

  val journal = PersistenceQuery(context.system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

  val decider: Supervision.Decider = {
    case NonFatal(ex) =>
      log.error(ex, "Got non fatal exception in ViewBuilder flow")
      Supervision.Resume
    case ex =>
      log.error(ex, "Got fatal exception in ViewBuilder flow, stream will be stopped")
      Supervision.Stop
  }

  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system).
      withSupervisionStrategy(decider))

  val resumableProjection = ResumableProjection(projectionId, context.system)
  resumableProjection.
    fetchLatestOffset.
    map(LatestOffsetResult.apply).
    pipeTo(self)

  def projectionId: String

  def receive = {
    case LatestOffsetResult(offset) =>
      val offsetDate = offset.getOrElse(NoOffset) match {
        case NoOffset =>
          clearIndex
          new Date(0L)
        case TimeBasedUUID(x) =>
          new Date()
      }
      val eventsSource = journal.eventsByTag(entityType, offset.getOrElse(NoOffset))
      //eventsSource.runForeach(self ! _)
      eventsSource.via(eventsFlow).runWith(Sink.ignore)
      log.info("Starting up view builder for entity {} with offset time of {}", entityType, offsetDate)
  }

  def actionFor(id: String, eventEnv: EventEnvelope): IndexAction

  val eventsFlow = {
    Flow[EventEnvelope].
      map { env =>
        val id = env.persistenceId.
          toLowerCase().drop(entityType.length() + 1)
        EnvelopeAndAction(env, actionFor(id, env))
      }.
      flatMapConcat {
        case ea @ EnvelopeAndAction(env, cr: DeferredCreate) =>
          Source.single(ea).via(cr.flow)
        case ea: EnvelopeAndAction =>
          Source.single(ea).via(Flow[EnvelopeAndAction])
      }.
      collect {
        case EnvelopeAndAction(env, i: InsertAction) =>
          EnvelopeAndFunction(env, () => updateIndex(i.id, i.rm, None))
        case EnvelopeAndAction(env, u: UpdateAction) =>
          EnvelopeAndFunction(env, () => updateDocumentField(u.id, env.sequenceNr - 1, u.expression, u.params))
        case EnvelopeAndAction(env, NoAction(id)) =>
          EnvelopeAndFunction(env, () => updateDocumentField(id, env.sequenceNr - 1, Nil, Map.empty[String, Any]))
      }.
      mapAsync(1) {
        case EnvelopeAndFunction(env, f) => f.apply.map(_ => env)
      }.
      mapAsync(1) { env =>
        env.offset match {
          case TimeBasedUUID(x) =>
            resumableProjection.storeLatestOffset(x)
        }
      }
  }

  //  def handlingEvents: Receive = {
  //    case LatestOffsetResult(offset) =>
  //      val offsetDate = offset.getOrElse(NoOffset) match {
  //        case NoOffset =>
  //          clearIndex
  //          new Date(0L)
  //        case TimeBasedUUID(x) =>
  //          new Date()
  //      }
  //
  //      val eventsSource = journal.eventsByTag(entityType, offset.getOrElse(NoOffset))
  //      //eventsSource.runForeach(self ! _)
  //      eventsSource.via(eventsFlow).runWith(Sink.ignore)
  //
  //      log.info("Starting up view builder for entity {} with offset time of {}", entityType, offsetDate)
  //    case env: EventEnvelope =>
  //      val updateProjection: PartialFunction[util.Try[IndexingResult], Unit] = {
  //        case tr =>
  //          env.offset match {
  //            case TimeBasedUUID(x) =>
  //              resumableProjection.storeLatestOffset(x)
  //          }
  //      }
  //
  //      log.info("sequence number is: {} ", env.sequenceNr);
  //
  //      val id = env.persistenceId.toLowerCase().drop(
  //        entityType.length() + 1)
  //      actionFor(id, env.offset, env.event) match {
  //        case i: InsertAction =>
  //          updateIndex(i.id, i.rm, None).
  //            andThen(updateProjection)
  //        case u: UpdateAction =>
  //          updateDocumentField(u.id,
  //            env.sequenceNr - 1, u.expression, u.params).
  //            andThen(updateProjection)
  //        case NoAction(id) =>
  //          updateDocumentField(id,
  //            env.sequenceNr - 1, Nil, Map.empty).
  //            andThen(updateProjection)
  //        case DeferredCreate =>
  //        //Nothing happening here
  //      }
  //  }

  def updateDocumentField(id: String, seq: Long, expressions: List[String], params: Map[String, Any]): Future[IndexingResult] = {
    val script = expressions.map(e => s"ctx._source.$e").mkString(";")
    val request = UpdateRequest(UpdateScript(script, params))
    updateIndex(id, request, Some(seq))
  }
}
