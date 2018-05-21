package com.navneetgupta.bookstore.credit

import java.util.Date
import com.navneetgupta.bookstore.common.EntityFieldsObject
import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityActor
import org.json4s.NoTypeHints
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }
import com.navneetgupta.bookstore.common.EntityActor.FinishCreate
import dispatch.Http
import dispatch._

object CreditTransactionStatus extends Enumeration {
  val Approved, Rejected = Value
}
case class CreditCardInfo(cardHolder: String, cardType: String, cardNumber: String, expiration: Date)

case class CreditCardTransactionFO(override val id: Int, cardInfo: CreditCardInfo, amount: Double, status: CreditTransactionStatus.Value,
                                   confirmationCode: Option[String], createTs: Date, modifyTs: Date, override val deleted: Boolean = false) extends EntityFieldsObject[Int, CreditCardTransactionFO] {
  override def assignId(id: Int) = this.copy(id = id)
  override def markDeleted = this.copy(deleted = true)
}

object CreditTransaction {

  def props(id: Int) = Props(classOf[CreditTransaction], id)

  implicit val formats = Serialization.formats(NoTypeHints)
  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)
  case class ChargeResponse(confirmationCode: String)
}

class CreditTransaction(idInput: Int) extends EntityActor[CreditCardTransactionFO](idInput) {
  import CreditTransaction._
  import context.dispatcher
  import akka.pattern._

  val settings = CreditSettings(context.system)
  val errorMapper = PartialFunction.empty
  val repo = new CreditTransactionRepository

  override def customCreateHandling: StateFunction = {
    case Event(fo: CreditCardTransactionFO, _) =>
      chargeCard(fo.cardInfo, fo.amount).
        map(resp => FinishCreate(fo.copy(confirmationCode = Some(resp.confirmationCode)))).
        to(self, sender())
      stay
  }

  def initializedHandling = PartialFunction.empty

  def chargeCard(cardInfo: CreditCardInfo, amount: Double) = {
    val jsonBody = write(ChargeRequest(cardInfo.cardHolder, cardInfo.cardType, cardInfo.cardNumber, cardInfo.expiration, amount))
    val request = url(settings.creditChargeUrl) << jsonBody
    Http(request OK as.String).map(read[ChargeResponse])
  }
}
