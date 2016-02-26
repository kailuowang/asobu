package asobu.distributed

import akka.actor.{ActorRef, ActorSelection}
import asobu.distributed.Extractors.RemoteExtractor
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
  def handlerActor: ActorRef
}

object EndpointDefinition {
  type Aux[T0] = EndpointDefinition { type T = T0 }
}

case class EndPointDefImpl[LExtracted <: HList](
    prefix: String,
    routeInfo: Route,
    remoteExtractor: RemoteExtractor[LExtracted],
    handlerActor: ActorRef
) extends EndpointDefinition {

  type T = LExtracted
  def extract(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[LExtracted] = remoteExtractor.run((routeParams, request))
}

/**
 * Endpoint that takes no input at all, just match a route path
 *
 * @param prefix
 * @param routeInfo
 * @param remoteActor
 */
case class NullaryEndPointDefinition(
    prefix: String,
    routeInfo: Route,
    handlerActor: ActorRef
) extends EndpointDefinition {

  type T = HNil
  def extract(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[T] = ExtractResult.pure(HNil)
}
