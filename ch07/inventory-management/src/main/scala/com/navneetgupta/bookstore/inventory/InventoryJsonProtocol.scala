package com.navneetgupta.bookstore.inventory

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.inventory.BookViewBuilder.BookRM
import com.navneetgupta.bookstore.inventory.InventoryClerk.CreateBook
import spray.json.RootJsonFormat
import java.util.Date
import spray.json.JsString
import spray.json.JsValue
import java.text.DateFormat
import spray.json.DeserializationException
import java.text.SimpleDateFormat
import scala.util.Try
import spray.json._

trait InventoryJsonProtocol extends BookstoreJsonProtocol {
  implicit val bookFoFormat = jsonFormat9(BookFO.apply)
  implicit val bookRmFormat = jsonFormat8(BookRM.apply)
  implicit val catalogBookFormat = jsonFormat4(CreateBook.apply)

}
