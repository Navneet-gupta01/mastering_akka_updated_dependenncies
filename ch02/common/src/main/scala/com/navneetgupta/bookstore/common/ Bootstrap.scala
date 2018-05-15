package com.navneetgupta.bookstore.common

import akka.actor.ActorSystem

trait Bootstrap {
  def bootstrap(system: ActorSystem): List[BookStorePlan]
}
