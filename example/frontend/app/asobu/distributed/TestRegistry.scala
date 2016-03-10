package asobu.distributed

import javax.inject.{Inject, Provider, Singleton}

import akka.actor._
import akka.cluster.Cluster
import asobu.distributed.gateway.Endpoint
import asobu.distributed.gateway.EndpointRegistry.Add
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import play.api.inject.Module
import play.api.mvc._

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._



class TestEndpointRegistry extends Actor with ActorLogging {

  import context.dispatcher

  def receive: Receive = running(Nil)

  import context.dispatcher
  import concurrent._

  context.system.scheduler.schedule(Duration.Zero, 10.seconds, self, MockRequest("/ep1/a/1", headers = Headers("bar" -> "BBBB")) )

  def running(endpoints: List[Endpoint]): Receive = {
    ({
      case Add(defs) ⇒
        context become running(endpoints ++ defs.map(Endpoint(_)))

    } : Receive) orElse receiveBy(endpoints)
  }


  private def receiveBy(endpoints: List[Endpoint]): Receive = {
    def toPartial(endpoint: Endpoint): Receive = {
      case req @ endpoint(rp) =>
        val rf = endpoint.handle(rp, req)
        rf.foreach( r => println("response back" + r))
    }
    endpoints.map(toPartial).foldLeft(PartialFunction.empty: Receive)(_ orElse _)
  }

}


case class MockRequest(path: String, headers: Headers = Headers()) extends Request[AnyContent] {
  def body: AnyContent = AnyContentAsJson(Json.obj())
  def secure: Boolean = ???
  def uri: String = ???
  def remoteAddress: String = ???
  def queryString: Map[String, Seq[String]] = Map.empty
  def method: String = "GET"
  def version: String = ???
  def tags: Map[String, String] = ???
  def id: Long = ???
}


@Singleton
class TestEndpointRegistryProvider @Inject()(system: ActorSystem) extends Provider[ActorRef] {
  val promise = Promise[ActorRef]
  Cluster(system) registerOnMemberUp {
    promise.success(system.actorOf(Props[TestEndpointRegistry], name = "endpoint-registry"))
  }

  def get(): ActorRef = Await.result(promise.future, 30.seconds) //intentionally blocking on cluster startup
}

class TestEndpointRegistryModule extends Module {

  def bindings(
                environment: Environment,
                configuration: Configuration
              ) = Seq(
    bind[TestEndpointRegistryProvider].toSelf.eagerly
  )
}
