package asobu.distributed

import akka.actor.ActorSelection
import asobu.dsl.{RequestExtractor, ExtractResult}
import asobu.dsl.util.HListOps.CombineTo
import play.api.mvc.{AnyContent, Request}
import play.core.routing.RouteParams
import play.routes.compiler.Route
import shapeless.{HNil, HList}
import cats.implicits._
import shapeless.ops.hlist.Prepend

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

case class EndPointDefImpl(
    prefix: String,
    routeInfo: Route,
    remoteExtractor: RemoteExtractor,
    remoteActor: ActorSelection
) extends EndpointDefinition {

  type T = remoteExtractor.T
  def extract(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[T] = remoteExtractor(routeParams, request)
}
