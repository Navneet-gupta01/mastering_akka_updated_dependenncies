package com.navneetgupta.bookstore.common

import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }
import akka.pattern._
import dispatch._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.typesafe.config.Config
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.AbstractActor.Receive
import akka.actor.ExtendedActorSystem
import java.nio.charset.Charset

object ElasticsearchApi {
  trait EsResponse
  case class ShardData(total: Int, failed: Int, successful: Int)
  case class IndexingResult(_shards: ShardData, _index: String,
                            _type: String, _id: String, _version: Int,
                            created: Option[Boolean]) extends EsResponse
  case class UpdateScript(source: String, params: Map[String, Any])
  case class UpdateRequest(script: UpdateScript)
  case class SearchHit(_source: JObject)
  case class QueryHits(hits: List[SearchHit])
  case class QueryResponse(hits: QueryHits) extends EsResponse
  case class DeleteResult(acknowledged: Boolean) extends EsResponse

  implicit val formats = Serialization.formats(NoTypeHints)
}

trait ElasticsearchSupport { me: BookstoreActor =>

  import ElasticsearchApi._
  val esSettings = ElasticsearchSettings(context.system)
  def indexRoot: String
  def entityType: String
  def baseUrl = s"${esSettings.rootUrl}/${indexRoot}/$entityType"
  //(implicit ec: ExecutionContext)
  def callElasticsearch[RT: Manifest](req: Req)(implicit ec: ExecutionContext): Future[RT] = {
    Http.default(req.setContentType("application/json", Charset.defaultCharset()) OK as.String).map(resp => read[RT](resp))
  }

  def queryElasticsearch(query: String)(implicit ec: ExecutionContext): Future[List[JObject]] = {
    val req = url(s"$baseUrl/_search") <<? Map("q" -> query)
    log.info("=======================================================================================================================================================")
    log.info("Request for QueryElastic Search is {}", req)
    log.info("=======================================================================================================================================================")
    callElasticsearch[QueryResponse](req).
      map(_.hits.hits.map(_._source))
  }

}

trait ElasticsearchUpdateSupport extends ElasticsearchSupport { me: ViewBuilder[_] =>

  import ElasticsearchApi._

  def updateIndex(id: String, request: AnyRef, version: Option[Long])(implicit ec: ExecutionContext): Future[IndexingResult] = {
    val urlBase = s"$baseUrl/$id"
    val requestUrl = version match {
      case None    => urlBase
      case Some(v) => s"$urlBase/_update?version=$v" //s"$urlBase/_update?version=$v"
    }
    log.info("=======================================================================================================================================================")
    log.info("requesturl is {}", requestUrl)
    log.info("=======================================================================================================================================================")
    log.info("Serialized Request Body is {}", write(request))
    log.info("=======================================================================================================================================================")
    val req = url(requestUrl) << write(request)
    log.info("Serialized Request Body is {}", write(request))
    log.info("=======================================================================================================================================================")
    callAndWait[IndexingResult](req)
  }

  def clearIndex(implicit ec: ExecutionContext) = {
    val req = url(s"${esSettings.rootUrl}/${indexRoot}/").DELETE
    callAndWait[DeleteResult](req)
  }

  def callAndWait[T <: AnyRef: Manifest](req: Req)(implicit ec: ExecutionContext) = {
    val fut = callElasticsearch[T](req)
    context.become(waitingForEsResult(req))
    fut pipeTo self
  }

  def waitingForEsResult(req: Req): Receive = {
    case es: EsResponse =>
      log.info("Successfully processed a request against the index for url: {}", req.toRequest.getUrl())
      context.become(handlingEvents)
      unstashAll
    case akka.actor.Status.Failure(ex) =>
      log.error(ex, "Error calling elasticsearch when building the read model")
      val wrappedEx = Option(ex.getCause())
      wrappedEx match {
        case Some(StatusCode(sc)) =>
          log.warning("Got a non-OK status code talking to elasticsearch: {}", sc)

        case other =>
          log.error(ex, "Error calling elasticsearch when building the read model")
      }
      context.become(handlingEvents)
      unstashAll
    case other =>
      stash
  }

}

class ElasticsearchSettingsImpl(conf: Config) extends Extension {
  val esConfig = conf.getConfig("elasticsearch")
  val host = esConfig.getString("host")
  val port = esConfig.getInt("port")
  val rootUrl = s"http://$host:$port"
}

object ElasticsearchSettings extends ExtensionId[ElasticsearchSettingsImpl] with ExtensionIdProvider {
  override def lookup = ElasticsearchSettings
  override def createExtension(system: ExtendedActorSystem) =
    new ElasticsearchSettingsImpl(system.settings.config)
}
