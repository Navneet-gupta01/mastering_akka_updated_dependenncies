package com.navneetgupta.bookstore.server

import java.util.Date
import com.navneetgupta.bookstore.common.BookstoreRoutesDefinition
import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import akka.stream.Materializer
import akka.http.scaladsl.server.Route
import java.text.SimpleDateFormat
import spray.json._
import scala.util.Try

object PretentCreditCardService extends BookstoreRoutesDefinition with BookstoreJsonProtocol {

  import akka.http.scaladsl.server.Directives._

  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)
  case class ChargeResponse(confirmationCode: String)
  implicit object DateFormatter extends JsonFormat[Date] {
    override def write(date: Date) = {
      JsString(dateToIsoString(date))
    }
    override def read(jv: JsValue) = jv match {
      case JsNumber(n) => new Date(n.longValue())
      case JsString(s) =>
        parseIsoDateString(s)
          .fold(deserializationError(s"Expected ISO Date format, got $s"))(identity)
      case other => throw new DeserializationException(s"expected JsString but got $other")
    }
  }

  private val localIsoDateFormatter = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  }

  private def dateToIsoString(date: Date) =
    date match {
      case null => localIsoDateFormatter.get().format(new Date())
      case _    => localIsoDateFormatter.get().format(date)
    }

  private def parseIsoDateString(date: String): Option[Date] =
    Try {
      date match {
        case null => new Date()
        case _    => localIsoDateFormatter.get().parse(date)
      }
    }.toOption
  implicit val chargeReqFormat = jsonFormat5(ChargeRequest)
  implicit val chargeRespFormat = jsonFormat1(ChargeResponse)

  override def routes(implicit system: ActorSystem, ec: ExecutionContext, mater: Materializer): Route = {
    path("credit" / "charge") {
      post {
        entity(as[ChargeRequest]) { req =>
          complete(ChargeResponse(java.util.UUID.randomUUID().toString))
        }
      }
    }
  }

  //  override def intent = {
  //    case req @ POST(Path(Seg("credit" :: "charge" :: Nil))) =>
  //      val body = Body.string(req)
  //      val request = read[ChargeRequest](body)
  //      val resp = write(ChargeResponse(java.util.UUID.randomUUID().toString))
  //      req.respond(JsonContent ~> ResponseString(resp))
  //  }
}
