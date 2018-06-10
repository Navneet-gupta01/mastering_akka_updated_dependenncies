package com.navneetgupta.bookstore.common

import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import akka.stream.Materializer
import akka.actor.ActorRef
import scala.reflect.ClassTag
import scala.concurrent.Future
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.StatusCodes._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import java.util.Date

object BookstoreRoutesDefinition {
  val NotFoundResp = ApiResponse[String](ApiResponseMeta(NotFound.intValue, Some(ErrorMessage("notfound"))))
  val UnexpectedFailResp = ApiResponse[String](ApiResponseMeta(InternalServerError.intValue, Some(ServiceResult.UnexpectedFailure)))
}

trait BookstoreRoutesDefinition extends ApiResponseJsonProtocol {
  import BookstoreRoutesDefinition._
  import concurrent.duration._
  implicit val endpointTimeout = Timeout(10 seconds)

  def routes(implicit system: ActorSystem, ec: ExecutionContext, mater: Materializer): Route

  def service[T: ClassTag](msg: Any, ref: ActorRef): Future[ServiceResult[T]] = {
    import akka.pattern.ask
    (ref ? msg).mapTo[ServiceResult[T]]
  }

  def serviceAndComplete[T: ClassTag](msg: Any, ref: ActorRef)(implicit format: JsonFormat[T]): Route = {
    val fut = service[T](msg, ref)
    onComplete(fut) {
      case util.Success(FullResult(t)) =>
        val resp = ApiResponse(ApiResponseMeta(OK.intValue), Some(t))
        complete(resp)

      case util.Success(EmptyResult) =>
        complete((NotFound, NotFoundResp))

      case util.Success(Failure(FailureType.Validation, ErrorMessage.InvalidEntityId, _)) =>
        complete((NotFound, NotFoundResp))

      case util.Success(fail: Failure) =>
        val status = fail.failType match {
          case FailureType.Validation => BadRequest
          case _                      => InternalServerError
        }
        val apiResp = ApiResponse[String](ApiResponseMeta(status.intValue, Some(fail.message)))
        complete((status, apiResp))

      case util.Failure(ex) =>
        complete((InternalServerError, UnexpectedFailResp))
    }
  }

}
