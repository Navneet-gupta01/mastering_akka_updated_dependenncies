package com.navneetgupta.bookstore.credit

import com.navneetgupta.bookstore.common.EntityAggregate
import java.util.Date
import akka.actor.Props
import java.util.UUID

object CreditAssociate {
  val Name = "credit-associate"
  def props = Props[CreditAssociate]
  case class ChargeCreditCard(cardInfo: CreditCardInfo, amount: Double)
}

class CreditAssociate extends EntityAggregate[CreditCardTransactionFO, CreditTransaction] {
  import context.dispatcher
  import CreditAssociate._

  val repo = new CreditTransactionRepository
  val settings = CreditSettings(context.system)

  override def receive = {
    case ChargeCreditCard(cardInfo, amount) =>
      //val id = UUID.randomUUID().toString
      val txn = lookupOrCreateChild(0)
      val fo = CreditCardTransactionFO(0, cardInfo, amount, CreditTransactionStatus.Approved, None, new Date, new Date)
      txn.forward(fo)
  }
  override def entityProps(id: Int): Props = CreditTransaction.props(id)
}
