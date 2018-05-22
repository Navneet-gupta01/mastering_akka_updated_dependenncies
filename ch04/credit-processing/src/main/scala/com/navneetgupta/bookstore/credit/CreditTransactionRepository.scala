package com.navneetgupta.bookstore.credit

import scala.concurrent.ExecutionContext
import com.navneetgupta.bookstore.common.BooksRepository
import com.navneetgupta.bookstore.common.EntityRepository
import scala.concurrent.Future
import slick.jdbc.GetResult

//object CreditTransactionRepository {
//  implicit val GetTxn = GetResult { r => CreditCardTransactionFO(r.<<, CreditCardInfo(r.<<, r.<<, r.<<, r.nextDate), r.<<, CreditTransactionStatus.withName(r.<<), r.<<, r.nextTimestamp, r.nextTimestamp, false) }
//}
//class CreditTransactionRepository(implicit val ec: ExecutionContext) extends EntityRepository[CreditCardTransactionFO] {
//
//  import DaoHelpers._
//  import slick.driver.PostgresDriver.api._
//  import slick.dbio.DBIOAction
//  import CreditTransactionRepository._
//
//  override def deleteEntity(id: Int): Future[Int] = Future.failed(new NotImplementedError("delete not implemented for credit card transaction"))
//
//  override def loadEntity(id: Int): Future[Option[CreditCardTransactionFO]] = {
//    val select = sql"select id, cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs from CreditCardTransaction where id = $id"
//    db.run(select.as[CreditCardTransactionFO]).map(_.headOption)
//  }
//  override def persistEntity(txn: CreditCardTransactionFO): Future[Int] = {
//    val info = txn.cardInfo
//    val insert = sqlu"""
//      insert into CreditCardTransaction (cardHolder, cardType, cardNumber, expiration, amount, status, confirmationCode, createTs, modifyTs)
//      values (${info.cardHolder}, ${info.cardType}, ${info.cardNumber}, ${info.expiration.toSqlDate}, ${txn.amount}, ${txn.status.toString}, ${txn.confirmationCode}, ${txn.createTs.toSqlDate}, ${txn.modifyTs.toSqlDate})
//    """
//    val getId = lastIdSelect("creditcardtransaction")
//    db.run(insert.andThen(getId).withPinnedSession).map(v => v.headOption.getOrElse(0))
//  }
//
//}
