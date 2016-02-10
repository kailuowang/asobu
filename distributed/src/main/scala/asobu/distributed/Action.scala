package asobu.distributed

import akka.actor.Actor
import asobu.distributed.Action.UnrecognizedMessage
import asobu.dsl.Extractor
import shapeless._

import scala.concurrent.Future

trait Action[T] {

  implicit val gen: LabelledGeneric[T]

  type Repr = gen.Repr

  def endpointDefinition: EndpointDefinition

  def extractor: Extractor[Repr]

  class RemoteHandler extends Actor {
    import context.dispatcher

    def receive: Receive = {
      case hlist: Repr ⇒

        val t: T = gen.from(hlist)
        val replyTo = sender
        backend(t).foreach(replyTo ! _)

      case _ ⇒ sender ! UnrecognizedMessage
    }

  }

  def backend[T, R](t: T): Future[R]
}

object Action {
  case object UnrecognizedMessage
}
