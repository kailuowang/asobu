package asobu.distributed.gateway

import akka.actor._
import akka.agent.Agent
import asobu.distributed.gateway.Endpoint.EndpointFactory
import asobu.distributed.gateway.EndpointRegistry._
import asobu.distributed.protocol._
import asobu.distributed.DedicatedDispatcher
import scala.concurrent.{ExecutionContext, Future}

/**
 * Internal API
 * This class is only supposed be used in the EndpointRegistryInterface actor.
 *
 * @param endpointFactory
 */
private[gateway] case class EndpointRegistry(
    endpointFactory: EndpointFactory,
    initData: Entries = Map.empty
)(implicit ex: ExecutionContext) {

  private val setsAgent = Agent(EndpointSets(Version.zero, initData))

  /**
   * Last update wins.
   *
   * @param set
   * @return
   */
  def sync(set: EndpointDefinitionSet): Future[EndpointSets] = {
    def update(exiting: EndpointSet): EndpointSet = {
      ???
    }
    val f = setsAgent.future()
    setsAgent.alter { sets ⇒
      val toSet = sets.entries.get(set.prefix).fold(endpointFactory(set))(update)
      val newEntries = sets.entries + (set.prefix → toSet)
      sets.update(newEntries)

    }
    f
  }

  def remove(handler: HandlerHost): Future[EndpointSets] = {
    ???
    setsAgent.future()
  }

  def get: EndpointSets = setsAgent.get
}

object EndpointRegistry {
  type Callback = EndpointSets ⇒ Unit
  /**
   * Also creates an actor inteface under the path /user/{@param name}
   *
   * @param endpointFactory
   * @param name
   * @param system
   */
  def create(endpointFactory: EndpointFactory, name: String = "asobu-endpoint-registry")(implicit system: ActorSystem): EndpointRegistryReader = {
    import DedicatedDispatcher.gateway._
    val registry = EndpointRegistry(endpointFactory)
    val interface = system.actorOf(Props(new EndpointRegistryInterface(registry)).dedicated, name)

    new EndpointRegistryReader {

      def get: EndpointSets = registry.get

      def subscribe(callback: (EndpointSets) ⇒ Unit): Unit = interface ! Subscribe(callback)
    }

  }

  private case object Updated
  private case class Subscribe(callback: Callback)

  class EndpointRegistryInterface(endpointRegistry: EndpointRegistry) extends Actor with ActorLogging {
    import context.dispatcher // this dispatcher is dedicated to asobu infra

    def receive = running(Nil)

    def running(callbacks: List[Callback]): Receive = {
      case Sync(set) ⇒
        val replyTo = sender
        endpointRegistry.sync(set).foreach { _ ⇒
          self ! Updated
          replyTo ! Synced(set)
        }

      case Remove(handler) ⇒
        val replyTo = sender
        endpointRegistry.remove(handler).foreach {
          _ ⇒
            self ! Updated
            replyTo ! Removed(handler)
        }

      case Subscribe(callback) ⇒
        context become running(callback :: callbacks)

      case Updated ⇒
        callbacks.foreach(_(endpointRegistry.get))
    }

  }

  type Entries = Map[Prefix, EndpointSet]

  case class EndpointSets(version: Version, entries: Entries) {
    def update(newEntries: Entries) =
      copy(version.incrementMinor, newEntries)
  }

}

trait EndpointRegistryReader {
  def get: EndpointSets
  def subscribe(callback: Callback): Unit
}

