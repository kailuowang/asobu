package asobu.distributed

import akka.actor._
import asobu.distributed.Action.{DistributedResult, DistributedRequest, UnrecognizedMessage}
import asobu.distributed.Endpoint.Prefix
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.mvc.{ResponseHeader, Result, AnyContent}
import play.routes.compiler.Route

import scala.concurrent.Future

trait Action {
  type TMessage

  val extractors: Extractors[TMessage]

  type ExtractedRemotely = extractors.LExtracted

  def name: String = getClass.getName.stripSuffix("$").replace('$', '.')

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

  def backend(t: TMessage): Future[DistributedResult]

}

object Action {
  type Aux[TMessage0] = Action { type TMessage = TMessage0 }

  case object UnrecognizedMessage

  case class HttpStatus(code: Int) extends AnyVal

  case class DistributedRequest[ExtractedT](extracted: ExtractedT, body: AnyContent)

  case class DistributedResult(
      status: HttpStatus,
      headers: Map[String, String] = Map.empty,
      body: Array[Byte] = Array.empty
  ) {
    def toResult = {
      Result(new ResponseHeader(status.code, headers), Enumerator(body))
    }
  }

  object DistributedResult {

    implicit def from(r: Result): Future[DistributedResult] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      (r.body run Iteratee.getChunks) map { chunks ⇒
        val body = chunks.toArray.flatten
        DistributedResult(HttpStatus(r.header.status), r.header.headers, body)

      }
    }
  }

}

