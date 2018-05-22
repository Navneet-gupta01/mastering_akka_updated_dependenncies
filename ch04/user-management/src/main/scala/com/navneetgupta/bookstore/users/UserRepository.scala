package com.navneetgupta.bookstore.users

//import scala.concurrent.ExecutionContext
//import com.navneetgupta.bookstore.common.EntityRepository
//import slick.jdbc.GetResult
//import scala.concurrent.Future

//object UserRepository {
//  val SelectFields = "select id, firstName, lastName, email, createTs, modifyTs from StoreUser "
//  implicit val GetUser = GetResult { r => UserFO(r.<<, r.<<, r.<<, r.<<, r.nextTimestamp, r.nextTimestamp) }
//}
//class UserRepository(implicit ec: ExecutionContext) extends EntityRepository[UserFO] {
//
//  import slick.driver.PostgresDriver.api._
//  import UserRepository._
//  import DaoHelpers._
//  import slick.dbio._
//
//  override def loadEntity(id: Int) = {
//    db.
//      run(sql"#$SelectFields where id = $id and not deleted".as[UserFO]).
//      map(_.headOption)
//  }
//
//  override def persistEntity(user: UserFO): Future[Int] = {
//    val insert = sqlu"""
//      insert into StoreUser (firstName, lastName, email, createTs, modifyTs)
//      values (${user.firstName}, ${user.lastName}, ${user.email}, ${user.createTs.toSqlDate}, ${user.modifyTs.toSqlDate})
//    """
//    val idget = lastIdSelect("storeuser")
//    db.run(insert.andThen(idget).withPinnedSession).map(_.headOption.getOrElse(0))
//  }
//
//  override def deleteEntity(id: Int) = {
//    val emailSuffix = s".$id.deleted"
//    db.run(sqlu"update StoreUser set deleted=true, email = email || $emailSuffix where id = ${id}")
//  }
//
//  def findUserIdByEmail(email: String) = {
//    db
//      .run(sql"select id from StoreUser where email = $email and not deleted".as[Int])
//      .map(_.headOption)
//  }
//
//  def updateUserInfo(user: UserFO) = {
//    val update = sqlu"""
//      update StoreUser set firstName = ${user.firstName},
//      lastName = ${user.lastName}, email = ${user.email} where id = ${user.id}
//    """
//    db.run(update)
//  }
//}
