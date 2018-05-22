package com.navneetgupta.bookstore.credit

import com.navneetgupta.bookstore.common.EntityAggregate
import java.util.Date
import akka.actor.Props
import java.util.UUID
import org.json4s._
import dispatch._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }
import org.json4s.NoTypeHints
import com.navneetgupta.bookstore.common.Aggregate
import com.navneetgupta.bookstore.common.Failure
import com.navneetgupta.bookstore.common.FailureType
import com.navneetgupta.bookstore.common.ServiceResult

object CreditAssociate {
  val Name = "credit-associate"
  def props = Props[CreditAssociate]
  case class ChargeCreditCard(cardInfo: CreditCardInfo, amount: Double)

  implicit val formats = Serialization.formats(NoTypeHints)
  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)
  case class ChargeResponse(confirmationCode: String)
}

class CreditAssociate extends Aggregate[CreditCardTransactionFO, CreditTransaction] {
  import context.dispatcher
  import CreditAssociate._

  //val repo = new CreditTransactionRepository
  val settings = CreditSettings(context.system)

  override def receive = {
    case ChargeCreditCard(info, amount) =>
      val caller = sender()
      chargeCard(info, amount).onComplete {
        case util.Success(result) =>
          val id = UUID.randomUUID().toString
          val fo = CreditCardTransactionFO(id, info, amount, CreditTransactionStatus.Approved, Some(result.confirmationCode), new Date)
          val txn = lookupOrCreateChild(id)
          txn.tell(CreditTransaction.Command.CreateCreditTransaction(fo), caller)

        case util.Failure(ex) =>
          log.error(ex, "Error performing credit charging")
          caller ! Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
      }
  }
  override def entityProps(id: String): Props = CreditTransaction.props(id)

  def chargeCard(info: CreditCardInfo, amount: Double) = {
    val jsonBody = write(ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount))
    val request = url(settings.creditChargeUrl) << jsonBody
    Http(request OK as.String).map(read[ChargeResponse])
  }
}
