package com.navneetgupta.bookstore.common

import akka.actor._
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext
import akka.util.Timeout

object EntityActor {
  case object GetFieldsObject
  case object Initialize
  case object Delete

  trait Data
  case object NoData extends Data
  case class InitializingData(id: Int) extends Data

  trait State
  case object Initializing extends State
  case object Creating extends State
  case object FailedToLoad extends State
  case object Missing extends State
  case object Initialized extends State
  case object Persisting extends State

  object NonStateTimeout {
    def unapply(any: Any) = any match {
      case FSM.StateTimeout => None
      case _                => Some(any)
    }
  }

  type ErrorMapper = PartialFunction[Throwable, Failure]

  case class Loaded[FO](fo: Option[FO])
  case class MissingData[FO](id: Int, deleted: Option[FO] = None) extends Data
  case class InitializedData[FO](fo: FO) extends Data
  case class PersistingData[FO](fo: FO, f: Int => FO, newInstance: Boolean = false) extends Data
  case class FinishCreate[FO](fo: FO)

}
abstract class EntityActor[FO <: EntityFieldsObject[Int, FO]](idInput: Int) extends BookstoreActor with FSM[EntityActor.State, EntityActor.Data] with Stash {
  import EntityActor._
  import akka.pattern.pipe
  import concurrent.duration._
  import context.dispatcher

  val repo: EntityRepository[FO]
  val entityType = getClass.getSimpleName
  val errorMapper: ErrorMapper

  if (idInput == 0)
    startWith(Creating, NoData)
  else {
    startWith(Initializing, InitializingData(idInput))
    self ! Initialize
  }

  when(Initializing) {
    case Event(Initialize, data: InitializingData) =>
      log.info("Initializing state data for {} {}", entityType, data.id)
      repo.loadEntity(data.id).map(fo => Loaded(fo)) pipeTo self
      stay
    case Event(Loaded(Some(fo)), _) =>
      log.info("Initialized state data {}", fo)
      unstashAll
      goto(Initialized) using InitializedData(fo)
    case Event(Loaded(None), data: InitializingData) =>
      log.error("No entity of type {} for id {}", entityType, idInput)
      unstashAll
      goto(Missing) using MissingData(data.id)
    case Event(Status.Failure(ex), data: InitializingData) =>
      log.error(ex, "Error initializing {} {}, stopping", entityType, data.id)
      goto(FailedToLoad) using data
    case Event(NonStateTimeout(other), _) =>
      log.info("NonStateTimeout from state Initializing {}", other)
      stash
      stay
  }

  when(Missing, 10 second) {
    case Event(GetFieldsObject, data: MissingData[FO]) =>
      log.info("FO missing msg received GetFieldsObject state is Missing")
      val result = data.deleted.map(FullResult.apply).getOrElse(EmptyResult)
      sender ! result
      stay

    case Event(NonStateTimeout(other), _) =>
      log.info("NonStateTimeout from state Missing")
      sender ! Failure(FailureType.Validation, ErrorMessage.InvalidEntityId)
      stay
  }

  when(Creating)(customCreateHandling orElse standardCreateHandling)

  def customCreateHandling: StateFunction = PartialFunction.empty

  def standardCreateHandling: StateFunction = {
    case Event(fo: FO, _) =>
      createAndRequestFO(fo)
    case Event(FinishCreate(fo: FO), _) =>
      createAndRequestFO(fo)
    case Event(Status.Failure(ex), _) =>
      log.error(ex, "Failed to create a new entity of type {}", entityType)
      val fail = mapError(ex)
      goto(Missing) using MissingData(0) replying (fail)
  }

  when(Initialized, 600 second)(standardInitializedHandling orElse initializedHandling)

  def standardInitializedHandling: StateFunction = {
    case Event(GetFieldsObject, InitializedData(fo)) =>
      log.info("Event GetFieldsObject in state Initialized")
      sender ! FullResult(fo)
      stay

    case Event(Delete, InitializedData(fo: FO)) =>
      requestFoForSender
      persist(fo, repo.deleteEntity(fo.id), _ => fo.markDeleted)
  }

  def initializedHandling: StateFunction

  when(Persisting) {
    case Event(i: Int, PersistingData(fo: FO, f: (Int => FO), newInstance)) =>
      val newFo: FO = f(i)
      unstashAll

      if (newFo.deleted) {
        goto(Missing) using MissingData(newFo.id, Some(newFo))
      } else {
        if (newInstance) {
          postCreate(newFo)
          setStateTimeout(Initialized, Some(1 second))
        }
        goto(Initialized) using InitializedData(newFo)
      }

    case Event(Status.Failure(ex), data: PersistingData[FO]) =>
      log.error(ex, "Failed on an create/update operation to {} {}", entityType, data.fo.id)
      val response = mapError(ex)
      goto(Initialized) using InitializedData(data.fo) forMax (1 second) replying (response)

    case Event(NonStateTimeout(other), _) =>
      stash
      stay
  }

  when(FailedToLoad, 5 second) {
    case Event(NonStateTimeout(other), _) =>
      sender ! Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
      stay
  }

  def createAndRequestFO(fo: FO) = {
    requestFoForSender
    persist(fo, repo.persistEntity(fo), id => fo.assignId(id), true)
  }

  whenUnhandled {
    case Event(StateTimeout, _) =>
      log.info("{} entity {} has reached max idle time, stopping instance", getClass.getSimpleName, self.path.name)
      stop
  }

  def persist(fo: FO, f: => Future[Int], foF: Int => FO, newInstance: Boolean = false) = {
    val daoResult = f
    daoResult.to(self, sender())
    goto(Persisting) using PersistingData(fo, foF, newInstance)
  }

  def postCreate(fo: FO) {}

  def mapError(ex: Throwable) =
    errorMapper.lift(ex).getOrElse(Failure(FailureType.Service, ServiceResult.UnexpectedFailure))

  def requestFoForSender: Unit = requestFoForSender(sender())
  def requestFoForSender(ref: ActorRef): Unit = self.tell(GetFieldsObject, ref)
}

trait EntityFieldsObject[K, FO] extends Serializable {
  def assignId(id: K): FO
  def id: K
  def deleted: Boolean
  def markDeleted: FO
}

abstract class EntityAggregate[FO <: EntityFieldsObject[Int, FO], E <: EntityActor[FO]: ClassTag] extends BookstoreActor {
  def lookupOrCreateChild(id: Int): ActorRef = {
    val name = entityActorName(id)
    log.info("name Of Actor is {}", name)
    context.child(name).getOrElse {
      log.info("Creating new {} actor to handle a request for id {}", entityName, id)
      if (id > 0)
        context.actorOf(entityProps(id), name)
      else
        context.actorOf(entityProps(id))
    }
  }

  def persistOperation(id: Int, msg: Any) {
    log.info("persistOperation for ID {} is with msg {}", id, msg)
    val entity = lookupOrCreateChild(id)
    entity.forward(msg)
  }

  def askForFo(bookActor: ActorRef) = {
    import akka.pattern.ask
    import concurrent.duration._
    log.info("AskForFO for actor : {}", bookActor.path)
    implicit val timeout = Timeout(5 seconds)
    (bookActor ? EntityActor.GetFieldsObject).mapTo[ServiceResult[FO]]
  }

  def multiEntityLookup(f: => Future[Vector[Int]])(implicit ex: ExecutionContext) = {
    log.info("multiEntityLookup for actor")
    for {
      ids <- f
      actors = ids.map(lookupOrCreateChild)
      fos <- Future.traverse(actors)(askForFo)
    } yield {
      FullResult(fos.flatMap(_.toOption))
    }
  }

  def entityProps(id: Int): Props

  private def entityName = {
    val entityTag = implicitly[ClassTag[E]]
    entityTag.runtimeClass.getSimpleName()
  }
  private def entityActorName(id: Int) = {
    log.info(s"entityName.toLowerCase: ${entityName.toLowerCase}");
    s"${entityName.toLowerCase}-$id"
  }

}
