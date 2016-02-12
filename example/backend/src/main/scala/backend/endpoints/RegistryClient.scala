package backend.endpoints

import akka.actor.Actor.Receive
import akka.actor._
import akka.cluster.Cluster
import akka.routing.FromConfig
import asobu.distributed.EndpointRegistry.{Extracted, RemoteHandlerDef}
import asobu.dsl.Extractor
import asobu.dsl.extractors.HeaderExtractors
import backend.endpoints.RegistryClient.Test
import cats.data.Xor
import play.api.mvc.{Result, AnyContent, Request}
import shapeless.labelled.FieldType
import shapeless.syntax.singleton._
import shapeless._; import record._

import scala.concurrent.Future
import scala.concurrent.duration._


class RegistryClient(registry: ActorRef) extends Actor with ActorLogging {


  def receive: Receive = ???
}


object RegistryClient {

  case object Test
  def startOn(system: ActorSystem) : Unit = {
    import system.dispatcher
    Cluster(system).registerOnMemberUp {
      val registry = system.actorOf(FromConfig.props(), name = "endpointsRegistryRouter")
      val client = system.actorOf(Props(new RegistryClient(registry)))
      system.scheduler.scheduleOnce(4.second, client, Test)
    }
  }
}
