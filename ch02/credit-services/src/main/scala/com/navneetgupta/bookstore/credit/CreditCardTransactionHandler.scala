package com.navneetgupta.bookstore.credit

import akka.actor.Props
import org.json4s.NoTypeHints
import java.util.Date
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }
import com.navneetgupta.bookstore.common.BookStoreActor
import com.navneetgupta.bookstore.domain.credit.ChargeCreditCard
import com.navneetgupta.bookstore.domain.credit.CreditCardInfo
import dispatch._
import com.navneetgupta.bookstore.domain.credit.CreditCardTransaction
import com.navneetgupta.bookstore.domain.credit.CreditTransactionStatus

object CreditCardTransactionHandler {
  val Name = "credit-handler"

  implicit val formats = Serialization.formats(NoTypeHints)
  def props = Props[CreditCardTransactionHandler]

  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)
  case class ChargeResponse(confirmationCode: String)
}

class CreditCardTransactionHandler extends BookStoreActor {

  import context.dispatcher
  import CreditCardTransactionHandler._

  val dao = new CreditCardTransactionHandlerDao
  val settings = CreditSettings(context.system)

  def receive = {
    case ChargeCreditCard(info, amount) =>
      val result =
        for {
          chargeResp <- chargeCard(info, amount)
          txn = CreditCardTransaction(0, info, amount, CreditTransactionStatus.Approved, Some(chargeResp.confirmationCode), new Date, new Date)
          daoResult <- dao.createCreditTransaction(txn)
        } yield daoResult
      pipeResponse(result)
  }

  def chargeCard(info: CreditCardInfo, amount: Double) = {
    val jsonBody = write(ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount))
    val request = url(settings.creditChargeUrl) << jsonBody
    Http(request OK as.String).map(read[ChargeResponse])
  }
}
