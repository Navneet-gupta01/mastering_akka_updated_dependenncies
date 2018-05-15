package com.navneetgupta.queuing

import akka.actor.ActorSystem

object ActorQueueApp extends App {
  val system = ActorSystem("queue")

  val queue = system.actorOf(ActorQueue.props, "Queue-Actor")

  val pairs = for (i <- 1 to 10) yield {
    val producer = system.actorOf(ProducerActor.props(queue))
    val consumer = system.actorOf(ConsumerActor.props(queue))
    (producer, consumer)
  }
  val reaper = system.actorOf(ShutdownReaper.props)
  pairs.foreach {
    case (producer, consumer) =>
      producer ! "start"
      consumer ! "start"
  }
}
