package com.navneetgupta.parallelism

import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask

object ParallelismWorkerApp extends App {
  // Try this example with arg as 1 and without any arg annd see the time diffrence
  implicit val timout = Timeout(60 seconds)

  val workerCount = args.headOption.getOrElse("8").toInt
  println(s"Using $workerCount worker instances")

  val system = ActorSystem("parallelism")
  import system.dispatcher
  sys.addShutdownHook(system.terminate)
  val master = system.actorOf(WorkMaster.props(workerCount), "master")
  val start = System.currentTimeMillis()
  (master ? WorkMaster.StartProcessing).
    mapTo[WorkMaster.IterationCount].
    flatMap { iterations ⇒
      val time = System.currentTimeMillis() - start
      println(s"total time was: $time ms")
      println(s"total iterations was: ${iterations.count}")
      system.terminate()
    }.recover {
      case t: Throwable ⇒
        t.printStackTrace()
        system.terminate()
    }
}
