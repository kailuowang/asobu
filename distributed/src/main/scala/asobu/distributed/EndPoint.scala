package asobu.distributed

import java.io.File

import play.api.mvc.{Handler, RequestHeader}
import play.core.routing
import play.core.routing.Route.ParamsExtractor
import play.core.routing.RouteParams
import play.routes.compiler._

case class Endpoint(routeInfo: Route, prefix: String) {

  import RoutesCompilerExtra._
  import routeInfo._
  lazy val defaultPrefix: String = {
    if (prefix.endsWith("/")) "" else "/"
  }

  lazy val routeExtractors: ParamsExtractor = {
    val localParts = if (path.parts.nonEmpty) StaticPart(defaultPrefix) +: path.parts else Nil
    routing.Route(verb.value, routing.PathPattern(toCPart(StaticPart(prefix) +: localParts)))
  }

  lazy val documentation: (String, String, String) = {
    val localPath = if (routeInfo.path.parts.isEmpty) ""
    else defaultPrefix + encodeStringConstant(routeInfo.path.toString)
    val pathInfo = prefix + localPath
    (verb.toString, pathInfo, call.toString)
  }

  def matchHandler(requestHeader: RequestHeader): Option[Handler] =
    routeExtractors.unapply(requestHeader).map(EndpointHandler(_, this))

  implicit private def toCPart(parts: Seq[PathPart]): Seq[routing.PathPart] = parts map {
    case DynamicPart(n, c, e) ⇒ routing.DynamicPart(n, c, e)
    case StaticPart(v)        ⇒ routing.StaticPart(v)
  }
}

case class EndpointDefs(defs: String, prefix: String = "/")

case class EndpointHandler(routeParams: RouteParams, endPoint: Endpoint) extends Handler

object Endpoint {

  def parse(endDefs: EndpointDefs): Either[Seq[RoutesCompilationError], List[Endpoint]] = {
    import cats.std.list._
    import cats.std.either._
    import cats.syntax.traverse._

    val placeholderFile = new File("remote-routes") //to conform to play api
    lazy val unsupportedError = Seq(RoutesCompilationError(placeholderFile, "doesn't support anything but route", None, None))

    RoutesFileParser.parseContent(endDefs.defs, placeholderFile).right.flatMap { routes ⇒
      routes.traverse[Either[Seq[RoutesCompilationError], ?], Endpoint] {
        case r: Route ⇒ Right(Endpoint(r, endDefs.prefix))
        case _        ⇒ Left(unsupportedError)
      }
    }
  }

}
