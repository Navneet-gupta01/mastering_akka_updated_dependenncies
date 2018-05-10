package com.navneetgupta.bookstore.book

import com.navneetgupta.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class BookBoot extends Bootstrap {
  override def bootstrap(system: ActorSystem) = {
    import system.dispatcher

    val bookManager = system.actorOf(BookManager.props, BookManager.Name)
    List(new BookEndpoint(bookManager))
  }
}
