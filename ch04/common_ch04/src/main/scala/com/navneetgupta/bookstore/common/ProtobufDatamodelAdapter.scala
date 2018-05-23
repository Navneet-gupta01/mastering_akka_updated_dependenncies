package com.navneetgupta.bookstore.common

import com.google.protobuf.Message
import akka.persistence.journal.EventAdapter
import akka.persistence.journal.EventSeq

trait DatamodelWriter {
  def toDatamodel: Message
}
trait DatamodelReader {
  def fromDatamodel: PartialFunction[Message, AnyRef]
}
class ProtobufDatamodelAdapter extends EventAdapter {

  override def fromJournal(event: Any, manifest: String): EventSeq = {
    event match {
      case m: Message =>
        val reader = Class.forName(manifest + "$").getField("MODULE$").get(null).asInstanceOf[DatamodelReader]
        println("reader is:  " + reader.toString())
        println(m)
        reader.
          fromDatamodel.
          lift(m).
          map(EventSeq.single).
          getOrElse(throw readException(event))

      case _ => throw readException(event)
    }
  }

  // Members declared in akka.persistence.journal.WriteEventAdapter
  override def manifest(event: Any): String = {
    println("event.getClass.getName: " + event.getClass.getName)
    event.getClass.getName
  }

  override def toJournal(event: Any): Any = event match {
    case wr: DatamodelWriter => wr.toDatamodel
    case _                   => throw new RuntimeException(s"Protobuf adapter can't write adapt type: $event")
  }

  private def readException(event: Any) = new RuntimeException(s"Protobuf adapter can't read adapt for type: $event")

}
