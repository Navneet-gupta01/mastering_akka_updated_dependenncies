package com.navneetgupta.bookstore.server

import io.netty.channel.ChannelHandler.Sharable
import unfiltered.netty.ServerErrorResponse
import unfiltered.request._
import org.json4s.NoTypeHints
import java.util.Date
import unfiltered.response.ResponseString
import unfiltered.response.JsonContent
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }

@Sharable
object PretentCreditCardService extends unfiltered.netty.async.Plan with ServerErrorResponse {
  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)
  case class ChargeResponse(confirmationCode: String)
  implicit val formats = Serialization.formats(NoTypeHints)

  override def intent = {
    case req @ POST(Path(Seg("credit" :: "charge" :: Nil))) =>
      val body = Body.string(req)
      val request = read[ChargeRequest](body)
      val resp = write(ChargeResponse(java.util.UUID.randomUUID().toString))
      req.respond(JsonContent ~> ResponseString(resp))
  }
}
