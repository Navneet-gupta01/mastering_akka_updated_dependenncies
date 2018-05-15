package com.navneetgupta.queuing

import akka.actor.ActorRef
import akka.actor.ActorLogging
import akka.actor.AbstractActor.Receive
import akka.actor.Actor
import akka.actor.Props

object ConsumerActor {
  def props(queue: ActorRef) = Props(classOf[ConsumerActor], queue)
}
class ConsumerActor(queue: ActorRef) extends Actor with ActorLogging {

  override def receive = consumerReceive(1000)

  def consumerReceive(remaining: Int): Receive = {
    case "start" =>
      queue ! ActorQueue.Dequeue
    case i: Int =>
      val newRemaining = remaining - 1
      if (newRemaining == 0) {
        log.info("Consumer {} is done consuming", self.path)
        context.stop(self)
      } else {
        queue ! ActorQueue.Dequeue
        context.become(consumerReceive(newRemaining))
      }
  }
}
