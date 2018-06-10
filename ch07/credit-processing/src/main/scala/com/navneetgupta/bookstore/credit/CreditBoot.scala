package com.navneetgupta.bookstore.credit

import com.navneetgupta.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class CreditBoot extends Bootstrap {
  override def bootstrap(system: ActorSystem) = {
    system.actorOf(CreditAssociate.props, CreditAssociate.Name)
    Nil
  }
}
