package com.navneetgupta.bookstore.server

import java.util.Date
import com.navneetgupta.bookstore.common.BookstoreRoutesDefinition
import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import akka.stream.Materializer
import akka.http.scaladsl.server.Route

object PretentCreditCardService extends BookstoreRoutesDefinition with BookstoreJsonProtocol {

  import akka.http.scaladsl.server.Directives._

  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)
  case class ChargeResponse(confirmationCode: String)
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
