package com.navneetgupta.bookstore.common

import unfiltered.netty._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import unfiltered.response._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }
import org.json4s.ext.EnumNameSerializer
import com.navneetgupta.bookstore.domain.order.SalesOrderStatus
import scala.util.Success
import io.netty.handler.codec.http.HttpResponse
import akka.util.Timeout

trait BookStorePlan extends async.Plan with ServerErrorResponse {
  import concurrent.duration._

  implicit val ec: ExecutionContext
  implicit val endPointTimeout = Timeout(10.seconds)
  implicit val formats = Serialization.formats(NoTypeHints) + new EnumNameSerializer(SalesOrderStatus)

  object InPathElement {
    def unapply(str: String): Option[Int] = util.Try(str.toInt).toOption
  }

  def respond(f: Future[Any], resp: unfiltered.Async.Responder[HttpResponse]) {
    f.onComplete {
      case util.Success(FullResult(b: AnyRef)) =>
        resp.respond(asJson(ApiResponse(ApiResponseMeta(Ok.code), Some(b))))
      case util.Success(EmptyResult) =>
        resp.respond(asJson(ApiResponse(ApiResponseMeta(statusCode = NotFound.code, error = Some(ErrorMessage("notfound")))), NotFound))
      case util.Success(fail: Failure) =>
        val status = fail.failType match {
          case FailureType.Validation => BadRequest
          case _                      => InternalServerError
        }
        val apiResp = ApiResponse(ApiResponseMeta(statusCode = status.code, error = Some(fail.message)))
        resp.respond(asJson(apiResp, status))
      case util.Success(res) =>
        val apiResp = ApiResponse(ApiResponseMeta(statusCode = InternalServerError.code, error = Some(ServiceResult.UnexpectedFailure)))
        resp.respond(asJson(apiResp, InternalServerError))

      case util.Failure(ex) =>
        val apiResp = ApiResponse(ApiResponseMeta(statusCode = InternalServerError.code, error = Some(ServiceResult.UnexpectedFailure)))
        resp.respond(asJson(apiResp, InternalServerError))
    }
  }

  def asJson[T <: AnyRef](resp: ApiResponse[T], status: Status = Ok) = {
    val ser = write(resp)
    status ~> JsonContent ~> ResponseString(ser)
  }

  val apiResp = ApiResponse(ApiResponseMeta(statusCode = InternalServerError.code, error = Some(ServiceResult.UnexpectedFailure)))
}
