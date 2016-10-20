package asobu.distributed.service

import java.net.URLEncoder
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import asobu.distributed._
import asobu.distributed.protocol.{EndpointDefinitionSet, Prefix, EndpointDefinition}
import scala.concurrent.{Future, ExecutionContext}

trait EndpointsRegistryClient {
  def add(endpointDefinitionSet: EndpointDefinitionSet): Future[EndpointDefinitionSet]
}
//
//case class EndpointsRegistryClientImp(
//    registry: EndpointsRegistry
//)(
//    implicit
//    ec: ExecutionContext,
//    system: ActorSystem,
//    ao: Timeout
//) extends EndpointsRegistryClient {
//
//  val clientActor = system.actorOf(EndpointsRegistryUpdater.props(registry), "asobu-endpoint-registry-client")
//
//  def add(endpointDefinitionSet: EndpointDefinitionSet): Future[EndpointDefinitionSet] =
//    (clientActor ? Add(endpointDefinitionSet)).map(_ ⇒ endpointDefinitionSet)
//
//}

