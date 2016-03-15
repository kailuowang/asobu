package backend.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import api.FactorialService
import api.FactorialService.Compute
import asobu.distributed.service.Action.DistributedResult
import asobu.distributed.service.{BodyExtractor, EndpointsRegistryClient, DistributedController, Controller}
import cats.data.Kleisli
import play.api.mvc.Results._
import concurrent.duration._

case class TestMeEndpoint(factorialBackend: ActorRef)(implicit sys: ActorSystem, epc: EndpointsRegistryClient) extends DistributedController {

  import concurrent.ExecutionContext.Implicits.global
  import asobu.dsl.DefaultExtractorImplicits._
  implicit val ao : Timeout = 30.seconds

  handle("ep1",
    process[Compute]())(
      using(factorialBackend).
        expect[FactorialService.Result].
        respond(r => Ok(r.result.toString))
    )
}
