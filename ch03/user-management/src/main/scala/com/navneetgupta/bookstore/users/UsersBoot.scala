package com.navneetgupta.bookstore.users

import com.navneetgupta.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class UsersBoot extends Bootstrap {
  override def bootstrap(system: ActorSystem) = {
    import system.dispatcher

    val customerRelationsManager = system.actorOf(CustomerRelationsManager.props, CustomerRelationsManager.Name)
    List(new UserEndpoint(customerRelationsManager))
  }
}
