package asobu.distributed

import java.io.File

import asobu.distributed.Endpoint.Prefix
import play.routes.compiler._

import scala.io.Source

object EndpointParser {

  private[distributed] def parse(
    prefix: Prefix,
    content: String,
    createEndpointDef: (Route, Prefix) ⇒ EndpointDefinition
  ): Either[Seq[RoutesCompilationError], List[EndpointDefinition]] = {
    parseContent(prefix, content, "remote-routes").right.map(_.map(createEndpointDef(_, prefix)))
  }

  def parseResource(
    prefix: Prefix,
    resourceName: String = "remote.routes"
  ): Either[Seq[RoutesCompilationError], List[Route]] = {
    val content = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream(resourceName)).mkString

    parseContent(prefix, content, resourceName)
  }

  def parseContent(
    prefix: Prefix,
    content: String,
    resourceName: String
  ): Either[Seq[RoutesCompilationError], List[Route]] = {
    import cats.std.list._
    import cats.std.either._
    import cats.syntax.traverse._

    val placeholderFile = new File(resourceName) //to conform to play api

    lazy val unsupportedError = Seq(RoutesCompilationError(placeholderFile, "doesn't support anything but route", None, None))
    RoutesFileParser.parseContent(content, placeholderFile).right.flatMap { routes ⇒
      routes.traverse[Either[Seq[RoutesCompilationError], ?], Route] {
        case r: Route ⇒ Right(r)
        case _        ⇒ Left(unsupportedError)
      }
    }
  }
}
