package com.navneetgupta.bookstore.common

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

case class ApiResponse[T](meta: ApiResponseMeta, response: Option[T] = None)

case class ApiResponseMeta(statusCode: Int, error: Option[ErrorMessage] = None, status: Boolean = true)

trait ApiResponseJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val errorMessageFormat = jsonFormat3(ErrorMessage.apply)
  implicit val metaFormat = jsonFormat3(ApiResponseMeta)
  implicit def apiRespFormat[T: JsonFormat] = jsonFormat2(ApiResponse.apply[T])
}
