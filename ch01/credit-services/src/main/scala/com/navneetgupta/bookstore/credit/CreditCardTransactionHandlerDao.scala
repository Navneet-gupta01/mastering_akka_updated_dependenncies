package com.navneetgupta.bookstore.credit

import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.BookStoreDao
import com.navneetgupta.bookstore.domain.credit.CreditCardTransaction

class CreditCardTransactionHandlerDao(implicit val ec: ExecutionContext) extends BookStoreDao {
  import DaoHelpers._
  import slick.driver.PostgresDriver.api._

  def createCreditTransaction(txn: CreditCardTransaction) = {
    val info = txn.creditCardInfo
    val insert = sqlu"""
      insert into CreditCardTransaction (cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs)
      values (${info.cardHolder}, ${info.cardType}, ${info.cardNumber}, ${info.expiration.toSqlDate}, ${txn.amount}, ${txn.status.toString}, ${txn.confirmationCode}, ${txn.createTs.toSqlDate}, ${txn.modifyTs.toSqlDate})
    """
    val getId = lastIdSelect("creditcardtransaction")
    db.run(insert.andThen(getId).withPinnedSession).map(v => txn.copy(id = v.headOption.getOrElse(0)))
  }
}
