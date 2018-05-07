package com.navneetgupta.bookstore.domain.credit

import java.util.Date

object CreditTransactionStatus extends Enumeration {
  val Approved, Rejected = Value
}

case class CreditCardInfo(cardHolder: String, cardType: String, cardNumber: String, expiration: Date)
case class CreditCardTransaction(id: Int, creditCardInfo: CreditCardInfo, amount: Double, status: CreditTransactionStatus.Value, confirmationCode: String, createTs: Date, modifyTs: Date)

case class ChargeCreditCard(cardInfo: CreditCardInfo, amount: Double)
