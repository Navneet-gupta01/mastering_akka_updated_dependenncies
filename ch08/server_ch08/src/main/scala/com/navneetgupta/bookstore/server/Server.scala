package com.navneetgupta.bookstore.server

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.event.Logging
import collection.JavaConversions._
import com.navneetgupta.bookstore.common.Bootstrap
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.stream.scaladsl.Sink

object Server extends App {
  import akka.http.scaladsl.server.Directives._
  val conf = ConfigFactory.load.getConfig("bookstore")
  //PostgresDb.init(conf)

  implicit val system = ActorSystem("Bookstore", conf)
  val log = Logging(system.eventStream, "Server")
  import system.dispatcher
  implicit val mater = ActorMaterializer()

  val endpoints =
    conf.
      getStringList("serviceBoots").
      map(toBootClass).
      flatMap(_.bootstrap(system)).
      map(_.routes)

  val definedRoutes = endpoints.reduce(_ ~ _)
  val finalRoutes =
    pathPrefix("api")(definedRoutes) ~
      PretentCreditCardService.routes //manually add in the pretend credit card service to the routing tree

  //  val server = endpoints.foldRight(unfiltered.netty.Server.http(8080)) {
  //    case (endpoint, serv) =>
  //      log.info("Adding endpoint: {}", endpoint)
  //      serv.plan(endpoint)
  //  }
  //
  //  //Adding in the pretend credit card charging service too so that the app works
  //  server.plan(PretentCreditCardService).run()

  val serverSource =
    Http().bind(interface = "0.0.0.0", port = 8080)
  val sink = Sink.foreach[Http.IncomingConnection](_.handleWith(finalRoutes))
  serverSource.to(sink).run

  def toBootClass(bootPrefix: String) = {
    val clazz = s"com.navneetgupta.bookstore.${bootPrefix.toLowerCase}.${bootPrefix}Boot"
    Class.forName(clazz).newInstance.asInstanceOf[Bootstrap]
  }
}
