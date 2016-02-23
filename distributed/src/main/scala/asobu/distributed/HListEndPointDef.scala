package asobu.distributed

import akka.actor.ActorSelection
import asobu.dsl.{Extractor, ExtractResult}
import asobu.dsl.util.HListOps.CombineTo
import play.api.mvc.{AnyContent, Request}
import play.core.routing.RouteParams
import play.routes.compiler.Route
import shapeless.HList
import cats.implicits._

case class HListEndPointDef[TRepr <: HList, ParamsRepr <: HList, RequestRepr <: HList](
    prefix: String,
    routeInfo: Route,
    requestExtractor: Extractor[RequestRepr],
    paramsExtractor: RouteParamsExtractor[ParamsRepr],
    remoteActor: ActorSelection
)(
    implicit
    combine: CombineTo[ParamsRepr, RequestRepr, TRepr]
) extends EndpointDefinition {

  type T = TRepr

  def extract(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[T] = {
    for {
      paramsRepr ← paramsExtractor.run(routeParams)
      requestRepr ← requestExtractor.run(request)
    } yield combine(paramsRepr, requestRepr)
  }

}
