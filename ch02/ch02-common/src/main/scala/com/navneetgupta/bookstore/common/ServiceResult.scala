package com.navneetgupta.bookstore.common

sealed abstract class ServiceResult[+A] {
  def isEmpty: Boolean
  def isValid: Boolean
  def getOrElse[B >: A](default: => B): B = default
  def map[B](f: A => B): ServiceResult[B] = EmptyResult
  def flatMap[B](f: A => ServiceResult[B]): ServiceResult[B] = EmptyResult
  def filter(f: A => Boolean): ServiceResult[A] = this
  def toOption = this match {
    case FullResult(a) => Some(a)
    case _             => None
  }
}

object ServiceResult {
  val UnexpectedFailure = ErrorMessage("common.unexpect", Some("An unexpected exception has occurred"))

  def fromOption[A](opt: Option[A]): ServiceResult[A] = opt match {
    case None        => EmptyResult
    case Some(value) => FullResult(value)
  }
}

sealed abstract class Empty extends ServiceResult[Nothing] {
  override def isEmpty: Boolean = true
  override def isValid: Boolean = false
}

case object EmptyResult extends Empty

final case class FullResult[+A](value: A) extends ServiceResult[A] {
  override def isEmpty: Boolean = false
  override def isValid: Boolean = true
  override def getOrElse[B >: A](default: => B): B = value
  override def map[B](f: A => B): ServiceResult[B] = FullResult(f(value))
  override def flatMap[B](f: A => ServiceResult[B]): ServiceResult[B] = f(value)
  override def filter(f: A => Boolean): ServiceResult[A] = if (f(value)) this else EmptyResult
}

object FailureType extends Enumeration {
  type FailureType = Value
  val Validation, Service = Value
}

case class ErrorMessage(code: String, shortMsg: Option[String] = None, params: Option[Map[String, String]] = None)

sealed case class Failure(failType: FailureType.Value, message: ErrorMessage, exception: Option[Throwable] = None) extends Empty {
  type A = Nothing
  override def map[B](f: A => B): ServiceResult[B] = this
  override def flatMap[B](f: A => ServiceResult[B]): ServiceResult[B] = this
}
