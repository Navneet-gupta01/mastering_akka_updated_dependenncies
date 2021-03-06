package com.navneetgupta.bookstore.common

case class ApiResponse[T <: AnyRef](meta: ApiResponseMeta, response: Option[T] = None)

case class ApiResponseMeta(statusCode: Int, status: Boolean = false, error: Option[ErrorMessage] = None)
