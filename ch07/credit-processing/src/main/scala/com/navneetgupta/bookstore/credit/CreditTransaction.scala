package com.navneetgupta.bookstore.credit

import java.util.Date
import com.navneetgupta.bookstore.common.EntityFieldsObject
import akka.actor.Props
import com.navneetgupta.bookstore.common.EntityActor
import com.navneetgupta.bookstore.common.EntityActor.FinishCreate
import com.navneetgupta.bookstore.common.EntityEvent
import com.navneetgupta.bookstore.common.DatamodelReader
import com.navneetgupta.bookstore.common.PersistentEntity

object CreditTransactionStatus extends Enumeration {
  val Approved, Rejected = Value
}
case class CreditCardInfo(cardHolder: String, cardType: String, cardNumber: String, expiration: Date)

object CreditCardTransactionFO {
  def empty = new CreditCardTransactionFO("", CreditCardInfo("", "", "", null), 0.0, CreditTransactionStatus.Rejected, None, null)
}
case class CreditCardTransactionFO(override val id: String, cardInfo: CreditCardInfo, amount: Double, status: CreditTransactionStatus.Value,
                                   confirmationCode: Option[String], createTs: Date, override val deleted: Boolean = false) extends EntityFieldsObject[String, CreditCardTransactionFO] {
  override def assignId(id: String) = this.copy(id = id)
  override def markDeleted = this.copy(deleted = true)
}

object CreditTransaction {

  def props(id: String) = Props(classOf[CreditTransaction], id)

  object Command {
    case class CreateCreditTransaction(txn: CreditCardTransactionFO)
  }
  trait CreditTransactionEvent extends EntityEvent { def entityType = "creditTransaction" }
  object Event {
    case class CreditTransactionCreated(txn: CreditCardTransactionFO) extends CreditTransactionEvent {
      def toDatamodel = {
        println("To DataModel CreditTransactionCreated")
        val cardInfoDm = Datamodel.CreditCardInfo.newBuilder().
          setCardHolder(txn.cardInfo.cardHolder).
          setCardNumber(txn.cardInfo.cardNumber).
          setCardType(txn.cardInfo.cardType).
          setExpiration(txn.cardInfo.expiration.getTime).
          build

        val builder = Datamodel.CreditCardTransaction.newBuilder().
          setAmount(txn.amount).
          setCardInfo(cardInfoDm).
          setCreateTs(txn.createTs.getTime).
          setId(txn.id).
          setStatus(txn.status.toString)

        txn.confirmationCode.foreach(c => builder.setConfirmationCode(c))

        Datamodel.CreditTransactionCreated.newBuilder().
          setTxn(builder.build).
          build
      }
    }
    object CreditTransactionCreated extends DatamodelReader {
      def fromDatamodel = {
        case dm: Datamodel.CreditTransactionCreated =>
          println("From DataModel CreditTransactionCreated")
          val txnDm = dm.getTxn()
          val cardInfoDm = txnDm.getCardInfo()
          val conf =
            if (txnDm.getConfirmationCode() != null) Some(txnDm.getConfirmationCode())
            else None

          CreditCardTransactionFO(txnDm.getId(), CreditCardInfo(cardInfoDm.getCardHolder(), cardInfoDm.getCardType(),
            cardInfoDm.getCardNumber(), new Date(cardInfoDm.getExpiration())), txnDm.getAmount(),
            CreditTransactionStatus.withName(txnDm.getStatus()), conf, new Date(txnDm.getCreateTs()))
      }
    }
  }
}

class CreditTransaction(idInput: String) extends PersistentEntity[CreditCardTransactionFO](idInput) {
  import CreditTransaction._
  import context.dispatcher
  import akka.pattern._

  val settings = CreditSettings(context.system)

  override def snapshotAfterCount = Some(5)

  def initialState: CreditCardTransactionFO = CreditCardTransactionFO.empty

  override def additionalCommandHandling: Receive = {
    case Command.CreateCreditTransaction(txn) =>
      persist(Event.CreditTransactionCreated(txn)) { handleEventAndRespond() }
  }

  def handleEvent(event: EntityEvent) = event match {
    case Event.CreditTransactionCreated(txn) =>
      state = txn
  }

  def isCreateMessage(cmd: Any): Boolean = cmd match {
    case cr: Command.CreateCreditTransaction => true
    case _                                   => false
  }

}
