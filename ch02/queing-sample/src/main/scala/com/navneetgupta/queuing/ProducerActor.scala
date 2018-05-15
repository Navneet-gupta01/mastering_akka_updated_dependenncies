package com.navneetgupta.queuing

import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef

object ProducerActor {
  def props(queue: ActorRef) = Props(classOf[ProducerActor], queue)
}
class ProducerActor(queue: ActorRef) extends Actor {
  override def receive = {
    case "start" =>
      for (i <- 1 to 1000) queue ! ActorQueue.Enqueue(i)
  }
}
