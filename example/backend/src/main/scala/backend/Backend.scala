package backend

import akka.actor._
import akka.cluster.Cluster
import akka.routing.FromConfig
import akka.util.Timeout
import asobu.distributed.gateway.EndpointRegistry.Add
import asobu.distributed.EndpointDefinition
import asobu.distributed.service.EndpointRegistryClient
import backend.endpoints.TestMeEndpoint
import com.typesafe.config.ConfigFactory
import backend.factorial._
import scala.collection.JavaConversions._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.util.Try

/**
 * Booting a cluster backend node with all actors
 */
object Backend extends App {

  // Simple cli parsing
  val port = args match {
    case Array()     => "0"
    case Array(port) => port
    case args        => throw new IllegalArgumentException(s"only ports. Args [ $args ] are invalid")
  }

  // System initialization
  val properties = Map(
      "akka.remote.netty.tcp.port" -> port
  )
  
  implicit val system: ActorSystem = ActorSystem("application", (ConfigFactory parseMap properties)
    .withFallback(ConfigFactory.load())
  )

  // Deploy actors and services
  FactorialBackend startOn system

  Cluster(system).registerOnMemberUp {
    val registry = system.actorOf(FromConfig.props(), name = "endpointsRegistryRouter")
    implicit val rec: EndpointRegistryClient = new EndpointRegistryClient {
      import akka.pattern.ask
      import concurrent.duration._
      import concurrent.ExecutionContext.Implicits.global
      implicit val ao: Timeout = 50.seconds //todo: be smarter about this
      def add(endpointDefinition: EndpointDefinition): Future[Unit] = (registry ? Add(List(endpointDefinition))).map(_ => ())
    }

    val initControllers = Try {
      TestMeEndpoint()
    }

    initControllers.recover {
      case e: Throwable =>
        system.log.error(e, s"Cannot initialize controllers, Exiting")
        system.terminate()
    }
  }

}
