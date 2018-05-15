package com.navneetgupta.bookstore.order

import org.scalatest.FlatSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar
import akka.actor.ActorSystem
import java.util.Date
import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import com.navneetgupta.bookstore.domain.user.FindUserById
import com.navneetgupta.bookstore.domain.credit.CreditTransactionStatus
import com.navneetgupta.bookstore.domain.order.SalesOrderLineItem
import com.navneetgupta.bookstore.domain.credit.CreditCardTransaction
import com.navneetgupta.bookstore.domain.order.SalesOrderStatus
import com.navneetgupta.bookstore.common.FullResult
import com.navneetgupta.bookstore.domain.order.LineItemRequest
import com.navneetgupta.bookstore.domain.book.FindBook
import com.navneetgupta.bookstore.domain.credit.ChargeCreditCard
import com.navneetgupta.bookstore.domain.order.CreateOrder
import com.navneetgupta.bookstore.domain.credit.CreditCardInfo
import com.navneetgupta.bookstore.domain.book.Book
import scala.concurrent.Future
import com.navneetgupta.bookstore.domain.order.SaleOrder
import com.navneetgupta.bookstore.domain.user.BookStoreUser
import org.mockito.Mockito._

class OrderProcessorUnitSpec extends FlatSpec with Matchers with BeforeAndAfterAll with MockitoSugar {
  import OrderProcessor._
  implicit val system = ActorSystem()

  class scoping extends TestKit(system) with ImplicitSender {
    val userMgr = TestProbe(UserManagerName)
    val bookMgr = TestProbe(OrderManager.BookMgrName)
    val creditHandler = TestProbe(CreditHandlerName)
    val namesMap =
      Map(
        UserManagerName -> userMgr.ref.path.name,
        OrderManager.BookMgrName -> bookMgr.ref.path.name,
        CreditHandlerName -> creditHandler.ref.path.name)

    val nowDate = new Date
    val mockDao = mock[OrderManagerDao]
    val orderProcessor = TestActorRef(new OrderProcessor {
      override val dao = mockDao
      override def newDate = nowDate
      override def lookup(name: String) =
        context.actorSelection(s"akka://default/system/${namesMap.getOrElse(name, "")}")
    })
  }

  "A request to create a new sales order" should
    """write a new order to the db and respond with
      that new order when everything succeeds""" in new scoping {

      val lineItem = LineItemRequest(2, 1)
      val cardInfo = CreditCardInfo("Chris Baxter", "Visa",
        "1234567890", new Date)
      val request = CreateOrder(1, List(lineItem), cardInfo)

      val expectedLineItem = SalesOrderLineItem(0, 0, lineItem.bookId,
        1, 19.99, nowDate, nowDate)
      val expectedOrder = SaleOrder(0, request.userId, 99,
        SalesOrderStatus.InProgress, 19.99, List(expectedLineItem),
        nowDate, nowDate)
      val finalOrder = expectedOrder.copy(id = 987)

      when(mockDao.createSalesOrder(expectedOrder)).
        thenReturn(Future.successful(finalOrder))

      orderProcessor ! request

      userMgr.expectMsg(FindUserById(request.userId))
      userMgr.reply(FullResult(BookStoreUser(request.userId,
        "Chris", "Baxter", "chris@masteringakka.com",
        new Date, new Date)))

      bookMgr.expectMsg(FindBook(lineItem.bookId))
      bookMgr.reply(FullResult(Book(lineItem.bookId,
        "20000 Leagues Under the Sea", "Jules Verne", 19.99,
        10, List("fiction"), new Date, new Date)))

      creditHandler.expectMsg(ChargeCreditCard(cardInfo, 19.99))
      creditHandler.reply(FullResult(CreditCardTransaction(99, cardInfo,
        19.99, CreditTransactionStatus.Approved, Some("abc123"),
        new Date, new Date)))

      expectMsg(FullResult(finalOrder))

    }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

}
