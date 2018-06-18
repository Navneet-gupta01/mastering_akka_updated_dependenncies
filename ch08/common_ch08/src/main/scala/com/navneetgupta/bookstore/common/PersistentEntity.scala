package com.navneetgupta.bookstore.common

import scala.reflect.ClassTag
import akka.persistence.PersistentActor
import akka.actor.ActorLogging
import akka.persistence.SnapshotOffer
import akka.persistence.RecoveryCompleted
import akka.actor.ReceiveTimeout
import akka.persistence.SaveSnapshotFailure
import akka.persistence.SaveSnapshotSuccess
import akka.actor.Props
import akka.actor.PoisonPill
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import akka.cluster.sharding.ShardRegion
import akka.actor.ActorSystem
import akka.cluster.sharding.ShardRegion.Passivate
import akka.cluster.sharding.ClusterSharding
import akka.cluster.sharding.ClusterShardingSettings

trait EntityEvent extends Serializable with DatamodelWriter {
  def entityType: String
}
trait EntityCommand {
  def entityId: String
}
object PersistentEntity {
  case object StopEntity
  case class GetState(id: String) extends EntityCommand {
    def entityId = id
  }
  case class MarkAsDeleted(id: String) extends EntityCommand {
    def entityId = id
  }

  class PersistentEntityIdExtractor(maxShards: Int) {
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case ec: EntityCommand => (ec.entityId, ec)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case ec: EntityCommand =>
        (math.abs(ec.entityId.hashCode) % maxShards).toString
    }
  }

  object PersistentEntityIdExtractor {

    def apply(system: ActorSystem): PersistentEntityIdExtractor = {
      val maxShards = system.settings.config.getInt("maxShards")
      new PersistentEntityIdExtractor(maxShards)
    }
  }

  def getPersistentEntityTimeout(config: Config, timeUnit: TimeUnit): Duration =
    Duration.create(config.getDuration("persistent-entity-timeout", TimeUnit.SECONDS), timeUnit)

}

abstract class PersistentEntity[FO <: EntityFieldsObject[String, FO]: ClassTag] extends PersistentActor with ActorLogging {
  import PersistentEntity._
  import scala.concurrent.duration._

  val id = self.path.name
  val entityType = getClass.getSimpleName
  var state: FO = initialState
  var eventsSinceLastSnapshot = 0

  context.setReceiveTimeout(1 minutes)

  override def persistenceId = s"$entityType-$id"

  override def receiveRecover = standardRecover orElse customRecover

  def standardRecover: Receive = {
    case ev: EntityEvent => {
      log.info("Recovering persisted event: {}", ev)
      handleEvent(ev)
      eventsSinceLastSnapshot += 1
    }

    case SnapshotOffer(metadata, snapshot: FO) => {
      log.info("Recovering entity with a snapshot: {}", snapshot)
      state = snapshot
    }

    case RecoveryCompleted =>
      log.debug("Recovery completed for {} entity with id {}", entityType, id)
  }

  def customRecover: Receive = PartialFunction.empty

  override def receiveCommand = standardCommandHandling orElse additionalCommandHandling

  def standardCommandHandling: Receive = {
    case ReceiveTimeout =>
      log.info("{} entity with id {} is being passivated due to inactivity", entityType, id)
      context.parent ! Passivate(stopMessage = StopEntity)
    //self ! PoisonPill
    case StopEntity =>
      log.info("{} entity with id {} is now being stopped due to inactivity", entityType, id)
      context stop self
    case any if !isAcceptingCommand(any) =>
      log.warning("Not allowing action {} on a deleted entity or an entity in the initial state with id {}", any, id)
      sender() ! stateResponse()

    case GetState(id) =>
      sender() ! stateResponse()
    case MarkAsDeleted =>
      newDeleteEvent match {
        case None =>
          log.info("The entity type {} does not support deletion, ignoring delete request", entityType)
          sender ! stateResponse()

        case Some(event) =>
          persist(event)(handleEventAndRespond(false))

      }
    case s: SaveSnapshotSuccess =>
      log.info("Successfully saved a new snapshot for entity {} and id {}", entityType, id)

    case f: SaveSnapshotFailure =>
      log.error(f.cause, "Failed to save a snapshot for entity {} and id {}, reason was {}", entityType)

  }

  def additionalCommandHandling: Receive = PartialFunction.empty

  def isAcceptingCommand(cmd: Any): Boolean = {
    !state.deleted &&
      !(state == initialState && !isCreateMessage(cmd))
  }

  def isCreateMessage(cmd: Any): Boolean

  def newDeleteEvent: Option[EntityEvent] = None

  def handleEventAndRespond(respectDeleted: Boolean = true)(event: EntityEvent) = {
    handleEvent(event)
    if (snapshotAfterCount.isDefined) {
      eventsSinceLastSnapshot += 1
      maybeSnapshot
    }
    sender() ! stateResponse(respectDeleted)
  }

  def stateResponse(respectDeleted: Boolean = true): ServiceResult[FO] = {
    //If we have not persisted this entity yet, then EmptyResult
    if (state == initialState) EmptyResult

    //If respecting deleted and it's marked deleted, EmptyResult
    else if (respectDeleted && state.deleted) EmptyResult

    //Otherwise, return it as a FullResult
    else FullResult(state)
  }

  def initialState: FO

  def handleEvent(event: EntityEvent): Unit

  def snapshotAfterCount: Option[Int] = None

  def maybeSnapshot: Unit = {
    snapshotAfterCount.
      filter(i => eventsSinceLastSnapshot >= i).
      foreach { i =>
        log.info("Taking snapshot because event count {} is > snapshot event limit of {}", eventsSinceLastSnapshot, i)
        saveSnapshot(state)
        eventsSinceLastSnapshot = 0
      }
  }

}

abstract class Aggregate[FO <: EntityFieldsObject[String, FO], E <: PersistentEntity[FO]: ClassTag] extends BookstoreActor {

  val idExtractor = PersistentEntity.PersistentEntityIdExtractor(context.system)
  val entityShardRegion =
    ClusterSharding(context.system).start(
      typeName = entityName,
      entityProps = entityProps,
      settings = ClusterShardingSettings(context.system),
      extractEntityId = idExtractor.extractEntityId,
      extractShardId = idExtractor.extractShardId)

  def forwardCommand(id: String, msg: Any): Unit = {
    //val entity = lookupOrCreateChild(id)
    entityShardRegion.forward(msg)
  }

  def entityProps: Props

  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName()
  }

}
