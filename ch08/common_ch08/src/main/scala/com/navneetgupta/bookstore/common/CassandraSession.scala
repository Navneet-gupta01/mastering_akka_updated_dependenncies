package com.navneetgupta.bookstore.common

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.datastax.driver.core.Session
import scala.concurrent.Future
import com.datastax.driver.core._
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import java.util.function.{ Function => JFunction }
import scala.concurrent.ExecutionContext
import akka.event.LoggingAdapter
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{ Failure => ScalaFailure, Success, Try }
import scala.util.control.NonFatal
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

class CassandraSession(system: ActorSystem, config: Config, init: Session => Future[_])(implicit ec: ExecutionContext, log: LoggingAdapter) {
  val cassandraHost = config.getString("host")

  val poolingOptions = new PoolingOptions()
  def cluster =
    Cluster.builder().
      addContactPoint(cassandraHost).
      withPoolingOptions(poolingOptions).
      build()

  // cache of PreparedStatement (PreparedStatement should only be prepared once)
  private val preparedStatements = new ConcurrentHashMap[String, Future[PreparedStatement]]
  private val computePreparedStatement = new JFunction[String, Future[PreparedStatement]] {
    override def apply(key: String): Future[PreparedStatement] =
      underlying().flatMap { s =>
        val prepared: Future[PreparedStatement] = s.prepareAsync(key)
        prepared.onComplete {
          case Success(_) => ()
          case ScalaFailure(_) =>
            // this is async, i.e. we are not updating the map from the compute function
            preparedStatements.remove(key)
        }
        prepared
      }
  }

  private val _underlyingSession = new AtomicReference[Future[Session]]()

  final def underlying(): Future[Session] = {

    def initialize(session: Future[Session]): Future[Session] = {
      session.flatMap { s =>
        val result = init(s)
        result.onComplete {
          case ScalaFailure(_) => close(s)
          case Success(_)      => ()
        }
        result.map(_ => s)
      }
    }

    @tailrec def setup(): Future[Session] = {
      val existing = _underlyingSession.get
      if (existing == null) {
        val s = initialize(Future(cluster.connect()))
        if (_underlyingSession.compareAndSet(null, s)) {
          s.onComplete {
            case Success(_)      => ()
            case ScalaFailure(e) => log.warning("Failed to connect to Cassandra and initialize. It will be retried on demand. Caused by: {}", e.getMessage)
          }
          system.registerOnTermination {
            s.foreach(close)
          }
          s
        } else {
          s.foreach(close)
          setup() // recursive
        }
      } else {
        existing
      }
    }

    val existing = _underlyingSession.get
    if (existing == null) {
      val result = retry(() => setup())
      result.onComplete {
        case Success(_)      => ()
        case ScalaFailure(e) => log.warning("Failed to connect to Cassandra and initialize. It will be retried on demand. Caused by: {}", e.getMessage)
      }
      result
    } else
      existing
  }

  private def retry(setup: () => Future[Session]): Future[Session] = {
    val promise = Promise[Session]

    def tryAgain(count: Int, cause: Throwable): Unit = {
      if (count == 0)
        promise.failure(cause)
      else {
        system.scheduler.scheduleOnce(1.second) {
          trySetup(count)
        }
      }
    }

    def trySetup(count: Int): Unit = {
      try {
        setup().onComplete {
          case Success(session) => promise.success(session)
          case ScalaFailure(cause) =>
            log.warning("Failure but trying again: ({}); {}", count - 1, Option(cause).map(_.getMessage).getOrElse("unknown error"))
            tryAgain(count - 1, cause)
        }
      } catch {
        case NonFatal(e) =>
          // this is only in case the direct calls, such as sessionProvider, throws
          promise.failure(e)
      }
    }

    trySetup(config.getInt("number-of-retries"))
    promise.future
  }

  private def close(s: Session): Unit = {
    s.closeAsync()
    s.getCluster().closeAsync()
  }

  def close(): Unit = {
    _underlyingSession.getAndSet(null) match {
      case null     =>
      case existing => existing.foreach(close)
    }
  }

  implicit def listenableFutureToFuture[A](lf: ListenableFuture[A])(implicit executionContext: ExecutionContext): Future[A] = {
    val promise = Promise[A]
    lf.addListener(new Runnable { def run() = promise.complete(Try(lf.get())) }, executionContext.asInstanceOf[Executor])
    promise.future
  }
}
