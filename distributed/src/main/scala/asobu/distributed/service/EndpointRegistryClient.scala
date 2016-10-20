package asobu.distributed.service

import akka.actor._
import akka.agent.Agent
import akka.cluster.Cluster
import akka.cluster.routing.{ClusterRouterGroupSettings, ClusterRouterGroup}
import akka.routing.{ScatterGatherFirstCompletedGroup, BroadcastGroup, RoundRobinGroup}
import akka.util.Timeout
import asobu.distributed.protocol.{Remove, Sync, HandlerHost, EndpointDefinitionSet}

import scala.concurrent.{Promise, Future}
import akka.pattern.ask
import asobu.distributed.Util.FutureOps
import cats.syntax.order._
import asobu.distributed.DedicatedDispatcher
import scala.concurrent.duration.{FiniteDuration, Duration}

class EndpointRegistryClient(registryName: String, registryRole: String)(
    implicit
    system: ActorSystem,
    askTimeout: Timeout
) {

  lazy val remoteRegistryRouterProps: Props = {
    val routeePath = List("/user/" + registryName)
    ClusterRouterGroup(
      ScatterGatherFirstCompletedGroup(routeePath, askTimeout.duration),
      ClusterRouterGroupSettings(
        totalInstances    = 200,
        routeesPaths      = routeePath,
        allowLocalRoutees = false,
        useRole           = Some(registryRole)
      )
    ).props()
  }

  def createSync(
    set: EndpointDefinitionSet,
    interval: FiniteDuration
  ): EndpointRegistrySync = new EndpointRegistrySync {
    import DedicatedDispatcher.service._
    val routerRef = system.actorOf(remoteRegistryRouterProps.dedicated)
    val promise = Promise[Unit]()
    val scheduler =
      system.scheduler.schedule(Duration.Zero, interval) {
        promise.tryCompleteWith((routerRef ? Sync(set)).void)
      }

    def synced = promise.future

    def cancelled = scheduler.isCancelled

    def unregister(): Future[Unit] = {
      scheduler.cancel()
      (routerRef ? Remove(HandlerHost.from(routerRef.path))).void
    }
  }
}

trait EndpointRegistrySync {

  def synced: Future[Unit]

  def cancelled: Boolean
  /**
   * remove self from the registry
   *
   * @return
   */
  def unregister(): Future[Unit]
}

