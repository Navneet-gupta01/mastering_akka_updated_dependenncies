package com.navneetgupta.bookstore.inventory

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.inventory.BookViewBuilder.BookRM
import com.navneetgupta.bookstore.inventory.InventoryClerk.CreateBook

trait InventoryJsonProtocol extends BookstoreJsonProtocol {
  implicit val bookFoFormat = jsonFormat9(BookFO.apply)
  implicit val bookRmFormat = jsonFormat8(BookRM.apply)
  implicit val catalogBookFormat = jsonFormat4(CreateBook.apply)
}
