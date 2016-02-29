package asobu.distributed

import java.io.File

import akka.actor.{ActorSelection, ActorRef}
import akka.util.Timeout
import asobu.distributed.Action.DistributedRequest
import asobu.dsl.{ExtractResult, RequestExtractor}
import play.api.mvc._, Results._
import play.core.routing
import play.core.routing.Route.ParamsExtractor
import play.core.routing.RouteParams
import play.routes.compiler._
import shapeless.{HNil, HList}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.pattern.ask
import cats.std.future._

trait EndpointRoute {
  def unapply(requestHeader: Request[AnyContent]): Option[RouteParams]
}

trait EndpointHandler {
  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Result]
}

case class Endpoint(definition: EndpointDefinition) extends EndpointRoute with EndpointHandler {
  import ExecutionContext.Implicits.global

  type T = definition.T

  import RoutesCompilerExtra._
  import definition._, definition.routeInfo._

  lazy val defaultPrefix: String = {
    if (prefix.value.endsWith("/")) "" else "/"
  }

  def unapply(request: Request[AnyContent]): Option[RouteParams] = routeExtractors.unapply(request)

  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Result] = {

    def handleMessageWithBackend(t: T): Future[Result] = {
      implicit val ak: Timeout = 10.minutes //todo: find the right place to configure this
      (handlerActor ? DistributedRequest(t, request.body)).collect {
        case r: Result ⇒ r
        case m         ⇒ InternalServerError(s"Unsupported result from backend ${m.getClass}")
      }
    }

    val message = definition.extract(routeParams, request)
    message.fold[Future[Result]](
      Future.successful(_),
      (t: T) ⇒ handleMessageWithBackend(t)
    ).flatMap(identity)

  }

  lazy val documentation: (String, String, String) = {
    val localPath = if (routeInfo.path.parts.isEmpty) ""
    else defaultPrefix + encodeStringConstant(routeInfo.path.toString)
    val pathInfo = prefix + localPath
    (verb.toString, pathInfo, call.toString)
  }

  private lazy val routeExtractors: ParamsExtractor = {
    val localParts = if (path.parts.nonEmpty) StaticPart(defaultPrefix) +: path.parts else Nil
    routing.Route(verb.value, routing.PathPattern(toCPart(StaticPart(prefix.value) +: localParts)))
  }

  implicit private def toCPart(parts: Seq[PathPart]): Seq[routing.PathPart] = parts map {
    case DynamicPart(n, c, e) ⇒ routing.DynamicPart(n, c, e)
    case StaticPart(v)        ⇒ routing.StaticPart(v)
  }

}

object Endpoint {
  case class Prefix(value: String) extends AnyVal

}
