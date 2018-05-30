package com.navneetgupta.bookstore.inventory

import com.navneetgupta.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class InventoryBoot extends Bootstrap {
  override def bootstrap(system: ActorSystem) = {
    import system.dispatcher

    val inventoryClerk = system.actorOf(InventoryClerk.props, InventoryClerk.Name)
    val bookView = system.actorOf(BookView.props, BookView.Name)
    system.actorOf(BookViewBuilder.props, BookViewBuilder.Name)
    List(new InventoryEndpoint(inventoryClerk, bookView))
  }
}
