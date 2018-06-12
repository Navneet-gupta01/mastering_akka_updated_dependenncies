package com.navneetgupta.bookstore.order

import com.navneetgupta.bookstore.common.ReadModelObject
import java.util.Date
import com.navneetgupta.bookstore.common.ViewBuilder
import akka.actor.Props
import akka.persistence.query.Offset
import com.navneetgupta.bookstore.order.SalesOrder.Event._
import com.navneetgupta.bookstore.inventory.InventoryClerk.FindBook
import com.navneetgupta.bookstore.inventory.InventoryClerk
import com.navneetgupta.bookstore.inventory.BookFO
import com.navneetgupta.bookstore.common.ViewBuilder.DeferredCreate
import com.navneetgupta.bookstore.common.ViewBuilder.UpdateAction
import com.navneetgupta.bookstore.common.ServiceResult
import com.navneetgupta.bookstore.common.FullResult
import com.navneetgupta.bookstore.order.SalesOrderViewBuilder.LineItemBook
import com.navneetgupta.bookstore.order.SalesOrderViewBuilder.SalesOrderLineItem
import com.navneetgupta.bookstore.order.SalesOrderViewBuilder.SalesOrderRM
import com.navneetgupta.bookstore.common.ElasticsearchSupport
import com.navneetgupta.bookstore.common.ElasticsearchApi
import com.navneetgupta.bookstore.common.BookstoreActor
import akka.persistence.query.TimeBasedUUID
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.navneetgupta.bookstore.common.ViewBuilder.EnvelopeAndAction
import com.navneetgupta.bookstore.common.ViewBuilder.InsertAction
import akka.stream.ActorMaterializer

object SalesOrderViewBuilder {
  val Name = "order-view-builder"
  case class LineItemBook(id: String, title: String, author: String,
                          tags: List[String])
  case class SalesOrderLineItem(lineItemNumber: Int,
                                book: LineItemBook, quantity: Int, cost: Double, status: String)
  case class SalesOrderRM(override val id: String,
                          userEmail: String, creditTxnId: String,
                          totalCost: Double, lineItems: Map[String, SalesOrderLineItem],
                          createTs: Date, deleted: Boolean = false) extends ReadModelObject
  def props = Props[SalesOrderViewBuilder]
}
trait SalesOrderReadModel {
  def indexRoot = "order"
  def entityType = SalesOrder.EntityType
}

class SalesOrderViewBuilder extends ViewBuilder[SalesOrderViewBuilder.SalesOrderRM] with SalesOrderReadModel with SalesOrderJsonProtocol {

  import akka.pattern.ask
  import concurrent.duration._
  implicit val timeout = Timeout(5 seconds)
  implicit val rmFormats = orderRmFormat

  def projectionId: String = "order-view-builder"

  val invClerk = context.actorSelection(s"/user/${InventoryClerk.Name}")

  val bookLookup =
    Flow[SalesOrderLineItemFO].
      mapAsyncUnordered(4)(item => (invClerk ? InventoryClerk.FindBook(item.bookId)).mapTo[ServiceResult[BookFO]]).
      fold(Map.empty[String, BookFO]) {
        case (books, FullResult(b)) => books ++ Map(b.id -> b)
        case (books, other) =>
          log.error("Encountered a non successful result looking up a book: {}", other)
          books
      }

  def actionFor(id: String, env: EventEnvelope): ViewBuilder.IndexAction = env.event match {
    case OrderCreated(order) =>
      //      order.lineItems.
      //        foreach(item => invClerk ! FindBook(item.bookId))
      //      context.become(loadingData(order,
      //        offset, Map.empty, order.lineItems.size))
      //      DeferredCreate
      val flow =
        Flow[EnvelopeAndAction].
          mapConcat { _ => order.lineItems }.
          via(bookLookup).
          map { books =>
            val lineItems = order.lineItems.flatMap { item =>
              books.get(item.bookId).map { b =>
                val itemBook = LineItemBook(b.id, b.title, b.author, b.tags)
                (item.lineItemNumber.toString, SalesOrderLineItem(item.lineItemNumber, itemBook, item.quantity, item.cost, item.status.toString))
              }
            }
            val salesOrderRm = SalesOrderRM(order.id, order.userId, order.creditTxnId,
              order.totalCost, lineItems.toMap, order.createTs, order.deleted)
            EnvelopeAndAction(env, InsertAction(id, salesOrderRm))
          }
      DeferredCreate(flow)
    case LineItemStatusUpdated(bookId, itemNumber, status) =>
      UpdateAction(id, s"lineItems['${itemNumber}'].status = params.newStatus", Map("newStatus" -> status.toString()))
  }

  //  def loadingData(order: SalesOrderFO, offset: Offset, books: Map[String, BookFO], needed: Int): Receive = {
  //    case sr: ServiceResult[_] =>
  //      val newNeeded = needed - 1
  //      val newBooks = sr match {
  //        case FullResult(b: BookFO) => books ++ Map(b.id -> b)
  //        case other =>
  //          log.error("Unexpected result waiting for book lookup, {}", other)
  //          books
  //      }
  //
  //      if (newNeeded <= 0) {
  //        //We have everything we need, build out the final read model object and save
  //        val lineItems = order.lineItems.flatMap { item =>
  //          newBooks.get(item.bookId).map { b =>
  //            val itemBook = LineItemBook(b.id, b.title, b.author, b.tags)
  //            (item.lineItemNumber.toString, SalesOrderLineItem(item.lineItemNumber, itemBook, item.quantity, item.cost, item.status.toString))
  //          }
  //        }
  //        val salesOrderRm = SalesOrderRM(order.id, order.userId, order.creditTxnId,
  //          order.totalCost, lineItems.toMap, order.createTs, order.deleted)
  //
  //        import context.dispatcher
  //        updateIndex(order.id, salesOrderRm, None)(context.dispatcher).andThen {
  //          case tr =>
  //            offset match {
  //              case TimeBasedUUID(x) =>
  //                resumableProjection.storeLatestOffset(x)
  //            }
  //        }
  //      } else {
  //        context.become(loadingData(order, offset, newBooks, newNeeded))
  //      }
  //
  //    case _ =>
  //      stash
  //  }

}

object SalesOrderView {
  val Name = "sales-order-view"
  case class FindOrdersForBook(bookId: String)
  case class FindOrdersForUser(email: String)
  case class FindOrdersForBookTag(tag: String)
  def props = Props[SalesOrderView]
}

class SalesOrderView extends SalesOrderReadModel with BookstoreActor with ElasticsearchSupport with SalesOrderJsonProtocol {
  import SalesOrderView._
  import ElasticsearchApi._
  import context.dispatcher
  implicit val mater = ActorMaterializer()

  def receive = {
    case FindOrdersForBook(bookId) =>
      val results = queryElasticsearch[SalesOrderRM](s"lineItems.\\*.book.id:$bookId")
      pipeResponse(results)

    case FindOrdersForUser(email) =>
      val results = queryElasticsearch[SalesOrderRM](s"userEmail:$email")
      pipeResponse(results)

    case FindOrdersForBookTag(tag) =>
      val results = queryElasticsearch[SalesOrderRM](s"lineItems.\\*.book.tags:$tag")
      pipeResponse(results)

  }
}
