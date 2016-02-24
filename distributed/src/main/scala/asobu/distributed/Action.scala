package asobu.distributed

import akka.actor.Actor
import asobu.distributed.Action.{UnrecognizedMessage}
import asobu.dsl.{ExtractResult, Extractor}
import play.api.mvc.{Result, AnyContent}
import play.core.routing.RouteParams
import shapeless._

import scala.concurrent.Future

trait Action[T] {

  implicit val gen: LabelledGeneric[T]

  type TRepr = gen.Repr

  type BodyRepr <: HList

  def extractBody(body: AnyContent): ExtractResult[BodyRepr]

  def endpointDefinition: EndpointDefinition

  def bodyExtractor

  class RemoteHandler extends Actor {
    import context.dispatcher

    def receive: Receive = {
      case hlist: TRepr @unchecked ⇒

        val t: T = gen.from(hlist)
        val replyTo = sender
        backend(t).foreach(replyTo ! _)

      case _ ⇒ sender ! UnrecognizedMessage
    }

  }

  def backend(t: T): Future[Result]

}

object Action {
  case object UnrecognizedMessage

  case class DistributedRequest[ExtractedT, Body <: AnyContent](extracted: ExtractedT, body: Body)

}

