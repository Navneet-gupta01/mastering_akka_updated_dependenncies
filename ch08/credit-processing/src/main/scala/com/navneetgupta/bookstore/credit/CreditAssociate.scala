package com.navneetgupta.bookstore.credit

import java.util.Date
import akka.actor.Props
import java.util.UUID
import com.navneetgupta.bookstore.common.Aggregate
import com.navneetgupta.bookstore.common.Failure
import com.navneetgupta.bookstore.common.FailureType
import com.navneetgupta.bookstore.common.ServiceResult
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import scala.concurrent.Future
import akka.http.scaladsl.Http
import spray.json._

object CreditAssociate {
  val Name = "credit-associate"
  def props = Props[CreditAssociate]
  case class ChargeCreditCard(cardInfo: CreditCardInfo, amount: Double)

  case class ChargeRequest(cardHolder: String, cardType: String, cardNumber: String, expiration: Date, amount: Double)
  case class ChargeResponse(confirmationCode: String)
}

class CreditAssociate extends Aggregate[CreditCardTransactionFO, CreditTransaction] with CreditJsonProtocol {
  import context.dispatcher
  import CreditAssociate._

  implicit val mater = ActorMaterializer()
  val settings = CreditSettings(context.system)

  override def receive = {
    case ChargeCreditCard(info, amount) =>
      val caller = sender()
      chargeCard(info, amount).onComplete {
        case util.Success(result) =>
          val id = UUID.randomUUID().toString
          val fo = CreditCardTransactionFO(id, info, amount, CreditTransactionStatus.Approved, Some(result.confirmationCode), new Date)
          //val txn = lookupOrCreateChild(id)
          entityShardRegion.tell(CreditTransaction.Command.CreateCreditTransaction(fo), caller)

        case util.Failure(ex) =>
          log.error(ex, "Error performing credit charging")
          caller ! Failure(FailureType.Service, ServiceResult.UnexpectedFailure)
      }
  }
  override def entityProps: Props = CreditTransaction.props

  //  def chargeCard(info: CreditCardInfo, amount: Double) = {
  //    val jsonBody = write(ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount))
  //    val request = url(settings.creditChargeUrl) << jsonBody
  //    Http(request OK as.String).map(read[ChargeResponse])
  //  }

  def chargeCard(info: CreditCardInfo, amount: Double): Future[ChargeResponse] = {
    val chargeReq = ChargeRequest(info.cardHolder, info.cardType, info.cardNumber, info.expiration, amount)
    val entity = HttpEntity(ContentTypes.`application/json`, chargeReq.toJson.prettyPrint)
    val request = HttpRequest(HttpMethods.POST, settings.creditChargeUrl, entity = entity)
    Http(context.system).
      singleRequest(request).
      flatMap {
        case resp if resp.status.isSuccess =>
          Unmarshal(resp.entity).to[ChargeResponse]
        case resp =>
          Future.failed(new RuntimeException(s"Invalid status code received on request: ${resp.status}"))
      }
  }
}
