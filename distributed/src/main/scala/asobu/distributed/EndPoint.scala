package asobu.distributed

import java.io.File

import akka.actor.{ActorSelection, ActorRef}
import akka.util.Timeout
import asobu.distributed.Action.DistributedRequest
import asobu.dsl.{ExtractResult, Extractor}
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
  def unapply(requestHeader: RequestHeader): Option[RouteParams]
}

trait EndpointHandler {
  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Result]
}

/**
 * Endpoint definition by the remote handler
 */
trait EndpointDefinition {
  /**
   * type of the Message
   */
  type T
  val routeInfo: Route
  val prefix: String
  def extract(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[T]
  def remoteActor: ActorSelection
}

object EndpointDefinition {
  type Aux[T0] = EndpointDefinition { type T = T0 }
}

object EmptyEnd

case class Endpoint(definition: EndpointDefinition) extends EndpointRoute with EndpointHandler {
  import ExecutionContext.Implicits.global

  type T = definition.T

  import RoutesCompilerExtra._
  import definition._, definition.routeInfo._

  lazy val defaultPrefix: String = {
    if (prefix.endsWith("/")) "" else "/"
  }

  def unapply(requestHeader: RequestHeader): Option[RouteParams] = routeExtractors.unapply(requestHeader)

  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Result] = {

    def handleMessageWithBackend(t: T): Future[Result] = {
      implicit val ak: Timeout = 10.minutes //todo: find the right place to configure this
      (remoteActor ? DistributedRequest(t, request.body)).collect {
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
    routing.Route(verb.value, routing.PathPattern(toCPart(StaticPart(prefix) +: localParts)))
  }

  implicit private def toCPart(parts: Seq[PathPart]): Seq[routing.PathPart] = parts map {
    case DynamicPart(n, c, e) ⇒ routing.DynamicPart(n, c, e)
    case StaticPart(v)        ⇒ routing.StaticPart(v)
  }

}

object Endpoint {

  def parse(content: String, prefix: String): Either[Seq[RoutesCompilationError], List[EndpointDefinition]] = {
    import cats.std.list._
    import cats.std.either._
    import cats.syntax.traverse._

    val placeholderFile = new File("remote-routes") //to conform to play api
    lazy val unsupportedError = Seq(RoutesCompilationError(placeholderFile, "doesn't support anything but route", None, None))

    def findEndPointDef(route: Route): EndpointDefinition = {
      HListEndPointDef(prefix, route, Extractor.empty, RouteParamsExtractor.empty, null) //todo replace this with real implementation
    }

    RoutesFileParser.parseContent(content, placeholderFile).right.flatMap { routes ⇒
      routes.traverse[Either[Seq[RoutesCompilationError], ?], EndpointDefinition] {
        case r: Route ⇒ Right(findEndPointDef(r))
        case _        ⇒ Left(unsupportedError)
      }
    }
  }

}
