package com.navneetgupta.queuing

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Stash

object ActorQueue {
  case class Enqueue(item: Int)
  case object Dequeue
  def props = Props[ActorQueue]
}
class ActorQueue extends Actor with ActorLogging with Stash {
  import ActorQueue._

  override def receive = emptyReceive

  def emptyReceive: Receive = {
    case Enqueue(item) =>
      context.become(nonEmptyReceive(List(item)))
      unstashAll()
    case Dequeue =>
      stash()
  }

  def nonEmptyReceive(items: List[Int]): Receive = {
    case Enqueue(item: Int) =>
      context.become(nonEmptyReceive(items :+ item))
    case Dequeue =>
      val item = items.head
      sender() ! item
      val newReceive = items.tail match {
        case Nil    => emptyReceive
        case nonNil => nonEmptyReceive(nonNil)
      }
      context.become(newReceive)
  }
}
