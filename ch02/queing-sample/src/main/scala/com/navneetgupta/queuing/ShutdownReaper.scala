package com.navneetgupta.queuing

import akka.actor.AbstractActor.Receive
import akka.actor.Terminated
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props

object ShutdownReaper {
  def props = Props[ShutdownReaper]
}
class ShutdownReaper extends Actor {

  override def receive = shutdownReceive(0)
  def shutdownReceive(watching: Int): Receive = {
    case ref: ActorRef ⇒
      context.watch(ref)
      context.become(shutdownReceive(watching + 1))

    case t: Terminated if watching - 1 == 0 ⇒
      println("All consumers done, terminating actor system")
      terminate()

    case t: Terminated ⇒
      context.become(shutdownReceive(watching - 1))
  }

  def terminate(): Unit =
    context.system.terminate()
}
