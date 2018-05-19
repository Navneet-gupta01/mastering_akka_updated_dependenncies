package com.navneetgupta.bookstore.domain.book

import java.util.Date

case class Book(id: Int, title: String, author: String, cost: Double, inventoryAmount: Int, tags: List[String], createTs: Date, modifyTs: Date, deleted: Boolean = false)

case class FindBook(id: Int)
case class FindBooksForId(ids: Seq[Int])
case class FindBooksByTags(tags: Seq[String])
case class FindBooksByTitle(title: String)
case class FindBookByAuthor(author: String)

case class CreateBook(title: String, author: String, tags: List[String], cost: Double)
case class AddTagToBook(bookId: Int, tag: String)
case class RemoveTagFromBook(bookId: Int, tag: String)
case class AddInventoryToBook(bookId: Int, amount: Int)
case class DeleteBook(bookId: Int)
