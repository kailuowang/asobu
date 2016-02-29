package asobu.distributed

import akka.actor._
import asobu.distributed.Action.{DistributedRequest, UnrecognizedMessage}
import asobu.distributed.Endpoint.Prefix
import play.api.mvc.{Result, AnyContent}
import play.routes.compiler.Route

import scala.concurrent.Future

trait Action {
  type TMessage

  val extractors: Extractors[TMessage]

  type ExtractedRemotely = extractors.LExtracted

  def name: String = getClass.getName.stripMargin('$').replace('$', '.')

  def endpointDefinition(route: Route, prefix: Prefix)(implicit arf: ActorRefFactory): EndpointDefinition = {
    val handlerActor = arf.actorOf(Props(new RemoteHandler).withDeploy(Deploy.local))
    EndPointDefImpl(prefix, route, extractors.remoteExtractor, handlerActor)
  }

  class RemoteHandler extends Actor {
    import context.dispatcher
    import cats.std.future._
    def receive: Receive = {
      case dr: DistributedRequest[extractors.LExtracted] @unchecked ⇒

        val tr = extractors.localExtract(dr)
        val replyTo = sender
        tr.map { t ⇒
          backend(t).foreach(replyTo ! _)
        }

      case _ ⇒ sender ! UnrecognizedMessage
    }

  }

  def backend(t: TMessage): Future[Result]

}

object Action {
  type Aux[TMessage0] = Action { type TMessage = TMessage0 }

  case object UnrecognizedMessage

  case class DistributedRequest[ExtractedT](extracted: ExtractedT, body: AnyContent)

}

