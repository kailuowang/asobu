package asobu.distributed

import java.io.File

import akka.actor.{ActorSelection, ActorRef}
import asobu.dsl.Extractor
import play.api.mvc.{Request, AnyContent, Handler, RequestHeader}
import play.core.routing
import play.core.routing.Route.ParamsExtractor
import play.core.routing.RouteParams
import play.mvc.Http.Response
import play.routes.compiler._
import shapeless.{HNil, HList}

import scala.concurrent.Future

trait EndpointRoute {
  def unapply(requestHeader: RequestHeader): Option[RouteParams]
}

trait EndpointHandler {
  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Response]
}

trait EndpointDefinition {
  /**
   * type of the Message
   */
  type T
  val routeInfo: Route
  val prefix: String
  val extractor: Extractor[T]
  def remoteActor: ActorSelection
}

case class EndpointDefinitionImpl[Repr <: HList](routeInfo: Route, prefix: String, extractor: Extractor[Repr]) extends EndpointDefinition {
  type T = Repr
  def remoteActor: ActorSelection = ???
}

case class Endpoint(definition: EndpointDefinition) extends EndpointRoute with EndpointHandler {
  type T = definition.T

  import RoutesCompilerExtra._
  import definition._, definition.routeInfo._

  lazy val defaultPrefix: String = {
    if (prefix.endsWith("/")) "" else "/"
  }

  private lazy val routeExtractors: ParamsExtractor = {
    val localParts = if (path.parts.nonEmpty) StaticPart(defaultPrefix) +: path.parts else Nil
    routing.Route(verb.value, routing.PathPattern(toCPart(StaticPart(prefix) +: localParts)))
  }

  lazy val documentation: (String, String, String) = {
    val localPath = if (routeInfo.path.parts.isEmpty) ""
    else defaultPrefix + encodeStringConstant(routeInfo.path.toString)
    val pathInfo = prefix + localPath
    (verb.toString, pathInfo, call.toString)
  }

  def unapply(requestHeader: RequestHeader): Option[RouteParams] = routeExtractors.unapply(requestHeader)
  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Response] = ???

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

    RoutesFileParser.parseContent(content, placeholderFile).right.flatMap { routes ⇒
      routes.traverse[Either[Seq[RoutesCompilationError], ?], EndpointDefinition] {
        case r: Route ⇒ Right(EndpointDefinitionImpl(r, prefix, Extractor.empty))
        case _        ⇒ Left(unsupportedError)
      }
    }
  }

}
