package com.navneetgupta.bookstore.common

import spray.json.JsonFormat
import spray.json._
import akka.pattern._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.typesafe.config.Config
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.AbstractActor.Receive
import akka.actor.ExtendedActorSystem
import java.nio.charset.Charset
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import scala.reflect.ClassTag

object ElasticsearchApi extends BookstoreJsonProtocol {
  trait EsResponse
  case class ShardData(total: Int, failed: Int, successful: Int)
  case class IndexingResult(_shards: ShardData, _index: String,
                            _type: String, _id: String, _version: Int,
                            created: Option[Boolean]) extends EsResponse
  case class UpdateScript(source: String, params: Map[String, Any])
  case class UpdateRequest(script: UpdateScript)
  case class SearchHit(_source: JsObject)
  case class QueryHits(hits: List[SearchHit])
  case class QueryResponse(hits: QueryHits) extends EsResponse
  case class DeleteResult(acknowledged: Boolean) extends EsResponse

  implicit val shardDataFormat = jsonFormat3(ShardData)
  implicit val indexResultFormat = jsonFormat6(IndexingResult)
  implicit val updateScriptFormat = jsonFormat2(UpdateScript)
  implicit val updateRequestFormat = jsonFormat1(UpdateRequest)
  implicit val searchHitFormat = jsonFormat1(SearchHit)
  implicit val queryHitsFormat = jsonFormat1(QueryHits)
  implicit val queryResponseFormat = jsonFormat1(QueryResponse)
  implicit val deleteResultFormat = jsonFormat1(DeleteResult)
}

trait ElasticsearchSupport { me: BookstoreActor =>

  import ElasticsearchApi._
  val esSettings = ElasticsearchSettings(context.system)
  def indexRoot: String
  def entityType: String
  def baseUrl = s"${esSettings.rootUrl}/${indexRoot}/$entityType"
  //(implicit ec: ExecutionContext)
  def callElasticsearch[RT: ClassTag](req: HttpRequest)(implicit ec: ExecutionContext, mater: Materializer, unmarshaller: Unmarshaller[ResponseEntity, RT]): Future[RT] = {
    // Http.default(req.setContentType("application/json", Charset.defaultCharset()) OK as.String).map(resp => read[RT](resp))
    Http(context.system).
      singleRequest(req).
      flatMap {
        case resp if resp.status.isSuccess =>
          Unmarshal(resp.entity).to[RT]
        case resp =>
          resp.discardEntityBytes()
          Future.failed(new RuntimeException(s"Unexpected status code of: ${resp.status}"))
      }
  }

  //  def queryElasticsearch(query: String)(implicit ec: ExecutionContext): Future[List[JObject]] = {
  //    val req = url(s"$baseUrl/_search") <<? Map("q" -> query)
  //  log.info("=======================================================================================================================================================")
  //  log.info("Request for QueryElastic Search is {}", req)
  //  log.info("=======================================================================================================================================================")
  //  //    callElasticsearch[QueryResponse](req).
  //      map(_.hits.hits.map(_._source))
  //  }

  def queryElasticsearch[RT](query: String)(implicit ec: ExecutionContext, mater: Materializer, jf: RootJsonFormat[RT]): Future[List[RT]] = {
    val req = HttpRequest(HttpMethods.GET, Uri(s"$baseUrl/_search").withQuery(Uri.Query(("q", query))))
    log.info("=======================================================================================================================================================")
    log.info("Request for QueryElastic Search is {}", req)
    log.info("=======================================================================================================================================================")

    callElasticsearch[QueryResponse](req).
      map(resp => {
        log.info("Recieved Response From Elastic Search API {}", resp)
        resp.hits.hits.map(_._source.convertTo[RT])
      })
  }

  def updateIndex[RT](id: String, request: RT, version: Option[Long])(implicit ec: ExecutionContext, jf: JsonFormat[RT], mater: Materializer): Future[IndexingResult] = {
    val urlBase = s"$baseUrl/$id"
    val requestUrl = version match {
      case None    => urlBase
      case Some(v) => s"$urlBase/_update?version=$v"
    }
    val entity = HttpEntity(ContentTypes.`application/json`, request.toJson.prettyPrint)
    val req = HttpRequest(HttpMethods.POST, requestUrl, entity = entity)
    callElasticsearch[IndexingResult](req)
  }

  def clearIndex(implicit ec: ExecutionContext, mater: Materializer) = {
    val req = HttpRequest(HttpMethods.DELETE, s"${esSettings.rootUrl}/${indexRoot}/")
    callElasticsearch[DeleteResult](req)
  }

}

//trait ElasticsearchUpdateSupport extends ElasticsearchSupport { me: ViewBuilder[_] =>
//
//  import ElasticsearchApi._
//
//  def updateIndex(id: String, request: AnyRef, version: Option[Long])(implicit ec: ExecutionContext): Future[IndexingResult] = {
//    val urlBase = s"$baseUrl/$id"
//    val requestUrl = version match {
//      case None    => urlBase
//      case Some(v) => s"$urlBase/_update?version=$v" //s"$urlBase/_update?version=$v"
//    }
//    log.info("=======================================================================================================================================================")
//    log.info("requesturl is {}", requestUrl)
//    log.info("=======================================================================================================================================================")
//    log.info("Serialized Request Body is {}", write(request))
//    log.info("=======================================================================================================================================================")
//    val req = url(requestUrl) << write(request)
//    log.info("Serialized Request Body is {}", write(request))
//    log.info("=======================================================================================================================================================")
//    callAndWait[IndexingResult](req)
//  }
//
//  def clearIndex(implicit ec: ExecutionContext) = {
//    val req = url(s"${esSettings.rootUrl}/${indexRoot}/").DELETE
//    callAndWait[DeleteResult](req)
//  }
//
//  def callAndWait[T <: AnyRef: Manifest](req: Req)(implicit ec: ExecutionContext) = {
//    val fut = callElasticsearch[T](req)
//    context.become(waitingForEsResult(req))
//    fut pipeTo self
//  }
//
//  def waitingForEsResult(req: Req): Receive = {
//    case es: EsResponse =>
//      log.info("Successfully processed a request against the index for url: {}", req.toRequest.getUrl())
//      context.become(receive)
//      unstashAll
//    case akka.actor.Status.Failure(ex) =>
//      log.error(ex, "Error calling elasticsearch when building the read model")
//      val wrappedEx = Option(ex.getCause())
//      wrappedEx match {
//        case Some(StatusCode(sc)) =>
//          log.warning("Got a non-OK status code talking to elasticsearch: {}", sc)
//
//        case other =>
//          log.error(ex, "Error calling elasticsearch when building the read model")
//      }
//      context.become(receive)
//      unstashAll
//    case other =>
//      stash
//  }
//
//}

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
