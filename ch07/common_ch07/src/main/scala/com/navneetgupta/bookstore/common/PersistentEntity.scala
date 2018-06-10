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

trait EntityEvent extends Serializable with DatamodelWriter {
  def entityType: String
}

object PersistentEntity {
  case object GetState
  case object MarkAsDeleted

  def getPersistentEntityTimeout(config: Config, timeUnit: TimeUnit): Duration =
    Duration.create(config.getDuration("persistent-entity-timeout", TimeUnit.SECONDS), timeUnit)

}

abstract class PersistentEntity[FO <: EntityFieldsObject[String, FO]: ClassTag](id: String) extends PersistentActor with ActorLogging {
  import PersistentEntity._
  import scala.concurrent.duration._

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
      context stop self
    //self ! PoisonPill
    case any if !isAcceptingCommand(any) =>
      log.warning("Not allowing action {} on a deleted entity or an entity in the initial state with id {}", any, id)
      sender() ! stateResponse()

    case GetState =>
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
  def lookupOrCreateChild(id: String) = {
    val name = entityActorName(id)
    context.child(name).getOrElse {
      log.info("Creating new {} actor to handle a request for id {}", entityName, id)
      context.actorOf(entityProps(id), name)
    }
  }

  def forwardCommand(id: String, msg: Any): Unit = {
    val entity = lookupOrCreateChild(id)
    entity.forward(msg)
  }

  def entityProps(id: String): Props

  private def entityActorName(id: String): String = s"${entityName.toLowerCase}-${id}"

  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName()
  }

}
