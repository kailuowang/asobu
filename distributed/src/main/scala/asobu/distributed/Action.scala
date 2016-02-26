package asobu.distributed

import akka.actor._
import asobu.distributed.Action.{DistributedRequest, UnrecognizedMessage}
import asobu.distributed.Extractors.BodyExtractor
import asobu.dsl.util.HListOps.{RestOf2, RestOf}
import asobu.dsl.util.RecordOps.FieldKVs
import asobu.dsl.{ExtractResult, RequestExtractor}
import play.api.mvc.{Result, AnyContent}
import play.core.routing.RouteParams
import play.routes.compiler.Route
import shapeless._
import shapeless.ops.hlist.Prepend

import scala.concurrent.Future

trait Action[TMessage] {
  def actorRefFactory: ActorRefFactory

  val messageExtractors: Extractors[TMessage]

  type ExtractedRemotely = messageExtractors.LExtracted

  /**
   * actor that handles
   */
  lazy val handlerActor: ActorRef = actorRefFactory.actorOf(Props(new RemoteHandler).withDeploy(Deploy.local))

  def endpointDefinition(prefix: String, route: Route): EndpointDefinition =
    EndPointDefImpl(prefix, route, messageExtractors.remoteExtractor, handlerActor)

  class RemoteHandler extends Actor {
    import context.dispatcher
    import cats.std.future._
    def receive: Receive = {
      case dr: DistributedRequest[messageExtractors.LExtracted] @unchecked ⇒

        val tr = messageExtractors.localExtract(dr)
        val replyTo = sender
        tr.map { t ⇒
          backend(t).foreach(replyTo ! _)
        }

      case _ ⇒ sender ! UnrecognizedMessage
    }

  }

  def backend(t: TMessage): Future[Result] = ???

}

object Action {
  case object UnrecognizedMessage

  case class DistributedRequest[ExtractedT](extracted: ExtractedT, body: AnyContent)

  def apply[TMessage](extractors: Extractors[TMessage])(implicit arf: ActorRefFactory): Action[TMessage] = new Action[TMessage] {
    def actorRefFactory = arf
    val messageExtractors = extractors
  }

}

