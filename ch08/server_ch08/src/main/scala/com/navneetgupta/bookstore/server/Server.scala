package com.navneetgupta.bookstore.server

import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.event.Logging
import collection.JavaConversions._
import com.navneetgupta.bookstore.common.Bootstrap
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.stream.scaladsl._

object Server extends App {
  import akka.http.scaladsl.server.Directives._
  val conf = ConfigFactory.load.getConfig("bookstore").resolve()
  //  //PostgresDb.init(conf)
  //  Thread.sleep(10000)

  println("Connfiguration to Start is : " + conf)

  implicit val system = ActorSystem("Bookstore", conf)
  implicit val mater = ActorMaterializer()
  val log = Logging(system.eventStream, "Server")
  import system.dispatcher

  val endpoints =
    conf.
      getStringList("serviceBoots").
      map(toBootClass).
      flatMap(_.bootstrap(system)).
      map(_.routes)

  val definedRoutes = endpoints.reduce(_ ~ _)
  val finalRoutes =
    pathPrefix("api")(definedRoutes) ~
      PretentCreditCardService.routes

  val serverSource =
    Http().bind(interface = "0.0.0.0", port = conf.getInt("httpPort"))
  val sink = Sink.foreach[Http.IncomingConnection](_.handleWith(finalRoutes))
  serverSource.to(sink).run

  def toBootClass(bootPrefix: String) = {
    val clazz = s"com.navneetgupta.bookstore.${bootPrefix.toLowerCase}.${bootPrefix}Boot"
    Class.forName(clazz).newInstance.asInstanceOf[Bootstrap]
  }
}
