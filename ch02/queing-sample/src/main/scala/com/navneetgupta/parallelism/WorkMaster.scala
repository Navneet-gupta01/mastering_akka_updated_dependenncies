package com.navneetgupta.parallelism

import akka.actor.Props
import akka.actor.Actor
import akka.routing.RoundRobinPool
import akka.actor.ActorRef

object WorkMaster {
  case object StartProcessing
  case object DoWorkerWork
  case class IterationCount(count: Long)
  def props(workerCount: Int) = Props(classOf[WorkMaster], workerCount)
}
class WorkMaster(workerCount: Int) extends Actor {
  import WorkMaster._

  val workers = context.actorOf(ParallelismWorker.props.withRouter(RoundRobinPool(workerCount)), "worker-count")

  override def receive = waitingForRequest

  def waitingForRequest: Receive = {
    case StartProcessing =>
      val requestCount = 50000
      for (i <- 1 to requestCount) {
        workers ! DoWorkerWork
      }
      context.become(collectingResults(requestCount, sender()))
  }

  def collectingResults(remaining: Int,
                        caller: ActorRef, iterations: Long = 0): Receive = {
    case IterationCount(count) =>
      val newRemaining = remaining - 1
      val newIterations = count + iterations
      if (newRemaining == 0) {
        caller ! IterationCount(newIterations)
        context.stop(self)
        context.system.terminate
      } else {
        context.become(
          collectingResults(newRemaining, caller, newIterations))
      }
  }
}
