package com.navneetgupta.parallelism

import akka.actor.Actor
import akka.actor.Props

object ParallelismWorker {
  def props = Props[ParallelismWorker]
}
class ParallelismWorker extends Actor {
  import WorkMaster._
  override def receive = {
    case DoWorkerWork =>
      var totalIterations = 0L
      var count = 10000000
      while (count > 0) {
        totalIterations += 1
        count -= 1
      }
      sender() ! IterationCount(totalIterations)
  }
}
