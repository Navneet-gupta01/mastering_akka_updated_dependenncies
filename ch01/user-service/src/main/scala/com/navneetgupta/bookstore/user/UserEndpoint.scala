package com.navneetgupta.bookstore.user

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext

class UserEndpoint(userManager: ActorRef)(implicit val ec: ExecutionContext) {

}
