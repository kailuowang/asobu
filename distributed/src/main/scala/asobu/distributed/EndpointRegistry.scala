package asobu.distributed

import javax.inject.{Inject, Provider, Singleton}

import akka.actor._
import akka.cluster.Cluster
import asobu.distributed.EndpointRegistry.{Extracted, RemoteHandlerDef}
import asobu.dsl.{RequestExtractor, Directive}
import cats.data.Xor
import play.api.mvc.{Result, Headers, AnyContent, Request}
import shapeless.HList

import scala.concurrent.{Future, Await, Promise}
import scala.concurrent.duration._

class EndpointRegistry extends Actor with ActorLogging {

  val mockRequest = new Request[AnyContent] {
    def body: AnyContent = ???

    def secure: Boolean = ???

    def uri: String = ???

    def remoteAddress: String = ???

    def queryString: Map[String, Seq[String]] = ???

    def method: String = ???

    def headers: Headers = Headers("wang" → "23")

    def path: String = ???

    def version: String = ???

    def tags: Map[String, String] = ???

    def id: Long = ???
  }
  import context.dispatcher

  def receive: Receive = {
    case RemoteHandlerDef(name, extractor) ⇒
      val replyTo = sender
      extractor.run(mockRequest).value.foreach {
        case Xor.Right(record) ⇒ replyTo ! Extracted(record)
        case _                 ⇒ //
      }
  }
}

object EndpointRegistry {
  case class RemoteHandlerDef[Repr <: HList](name: String, extractor: RequestExtractor[Repr])
  case class Extracted[Repr <: HList](r: Repr)
}

@Singleton
class EndpointRegistryProvider @Inject() (system: ActorSystem) extends Provider[ActorRef] {
  val promise = Promise[ActorRef]
  Cluster(system) registerOnMemberUp {
    promise.success(system.actorOf(Props[EndpointRegistry], name = "endpoint-registry"))
  }

  def get(): ActorRef = Await.result(promise.future, 30.seconds) //intentionally blocking on cluster startup
}
