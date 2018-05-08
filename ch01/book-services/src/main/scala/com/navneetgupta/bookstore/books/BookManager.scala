package com.navneetgupta.bookstore.books

import com.navneetgupta.bookstore.common.BookStoreActor
import akka.actor.Props

object BookManager {
  val Name = "book-manager"
  val props = Props[BookManager]

}

class BookManager extends BookStoreActor {
  import context.dispatcher

  val dao = new BookManagerDao

  override def receive = {
    case
  }
}
