package com.navneetgupta.bookstore.order

import com.navneetgupta.bookstore.common.Bootstrap
import akka.actor.ActorSystem

class OrderBoot extends Bootstrap {
  override def bootstrap(system: ActorSystem) = {
    import system.dispatcher

    val salesAssociate = system.actorOf(SalesAssociate.props, SalesAssociate.Name)
    val salesOrderView = system.actorOf(SalesOrderView.props, SalesOrderView.Name)
    system.actorOf(SalesOrderViewBuilder.props, SalesOrderViewBuilder.Name)
    List(new SalesOrderEndpoint(salesAssociate, salesOrderView))
  }
}
