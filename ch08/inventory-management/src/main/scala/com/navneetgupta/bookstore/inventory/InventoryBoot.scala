package com.navneetgupta.bookstore.inventory

import com.navneetgupta.bookstore.common.Bootstrap
import akka.actor.ActorSystem
import io.netty.channel.ChannelHandler.Sharable

class InventoryBoot extends Bootstrap {
  override def bootstrap(system: ActorSystem) = {
    import system.dispatcher

    val inventoryClerk = system.actorOf(InventoryClerk.props, InventoryClerk.Name)
    val bookView = system.actorOf(BookView.props, BookView.Name)
    startSingleton(system, BookViewBuilder.props, BookViewBuilder.Name)
    List(new InventoryEndpoint(inventoryClerk, bookView))
  }
}
