package com.navneetgupta.bookstore.credit

import com.navneetgupta.bookstore.common.BookstoreJsonProtocol
import com.navneetgupta.bookstore.credit.CreditAssociate.{ ChargeRequest, ChargeResponse }

trait CreditJsonProtocol extends BookstoreJsonProtocol {
  implicit val chargeReqFormat = jsonFormat5(ChargeRequest)
  implicit val chargeRespFormat = jsonFormat1(ChargeResponse)
}
