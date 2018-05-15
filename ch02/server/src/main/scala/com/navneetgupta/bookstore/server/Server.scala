package com.navneetgupta.bookstore.server

import com.typesafe.config.ConfigFactory
import com.navneetgupta.bookstore.common.PostgresDb
import akka.actor.ActorSystem
import akka.event.Logging
import collection.JavaConversions._
import com.navneetgupta.bookstore.common.Bootstrap

object Server extends App {
  val conf = ConfigFactory.load.getConfig("bookstore")
  PostgresDb.init(conf)

  implicit val system = ActorSystem("Bookstore", conf)
  val log = Logging(system.eventStream, "Server")
  import system.dispatcher

  val endpoints =
    conf.
      getStringList("serviceBoots").
      map(toBootClass).
      flatMap(_.bootstrap(system))

  val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)) {
    case (endpoint, serv) =>
      log.info("Adding endpoint: {}", endpoint)
      serv.plan(endpoint)
  }

  //Adding in the pretend credit card charging service too so that the app works
  server.plan(PretentCreditCardService).run()

  def toBootClass(bootPrefix: String) = {
    val clazz = s"com.navneetgupta.bookstore.${bootPrefix.toLowerCase}.${bootPrefix}Boot"
    Class.forName(clazz).newInstance.asInstanceOf[Bootstrap]
  }
}
