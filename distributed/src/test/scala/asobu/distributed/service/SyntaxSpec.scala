package asobu.distributed.service

import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import akka.util.Timeout
import asobu.distributed.{PredefinedDefs, EndpointDefinition}
import asobu.distributed.util.{ScopeWithActor, SpecWithActorSystem, SerializableTest}
import asobu.dsl.extractors.JsonBodyExtractor
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import concurrent.duration._
import scala.concurrent.Future

class SyntaxSpec extends SpecWithActorSystem with SerializableTest with Controller with Syntax with PredefinedDefs {
  import asobu.distributed.service.SyntaxSpec._
  implicit val format = Json.format[Input]
  import asobu.dsl.DefaultExtractorImplicits._

  implicit val endpointResgitryClient: EndpointsRegistryClient = new EndpointsRegistryClient {
    def add(endpointDefinition: EndpointDefinition): Future[Unit] = Future.successful(())
  }

  implicit val ao: Timeout = 1.seconds
  val testBE = system.actorOf(testBackend)

  def is(implicit ee: ExecutionEnv) = {
    "can build endpoint extracting params only" >> new ScopeWithActor {
      val endpointF = handle(
        "anEndpoint",
        process[Input]()
      )(using(testBE).expect[Output].respond(Ok))

      endpointF.map(isSerializable) must beTrue.await
    }

    "can build endpoint extracting params and body only" >> new ScopeWithActor {
      val endpointF = handle(
        "anEndpoint",
        process[Input](BodyExtractor.jsonList[Input])
      )(using(testBE).expect[Output].respond(Ok))

      endpointF.map(isSerializable) must beTrue.await
    }

    "can build endpoint extracting param body, and header" >> new ScopeWithActor {
      val endpointF = handle(
        "anEndpoint",
        process[LargeInput](
          from(flagInHeader = header[Boolean]("someheaderField")),
          BodyExtractor.jsonList[Input]
        )
      )(using(testBE).expect[Output].respond(Ok))

      endpointF.map(isSerializable) must beTrue.await
    }
  }

}

object SyntaxSpec {
  class TestBackend extends Actor {
    def receive: Receive = {
      case Input(a, b) ⇒ sender ! Output(a + b)
    }
  }

  def testBackend: Props = Props(new TestBackend)
  case class LargeInput(a: String, b: Int, flagInHeader: Boolean)
  case class Input(a: String, b: Int)
  case class Output(a: String)
}
