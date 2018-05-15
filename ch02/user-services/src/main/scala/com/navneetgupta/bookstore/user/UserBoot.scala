package com.navneetgupta.bookstore.user

import com.navneetgupta.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class UserBoot extends Bootstrap {
  override def bootstrap(system: ActorSystem) = {
    import system.dispatcher

    val userManager = system.actorOf(UserManager.props, UserManager.Name)
    List(new UserEndpoint(userManager))
  }
}
