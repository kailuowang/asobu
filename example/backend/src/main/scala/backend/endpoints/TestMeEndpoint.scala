package backend.endpoints

import akka.actor.{ActorRefFactory, ActorSystem}
import asobu.distributed.Action
import asobu.distributed.Action.DistributedResult
import asobu.distributed.Controller
import asobu.distributed._
import asobu.dsl.RequestExtractor
import asobu.dsl.extractors.HeaderExtractors
import play.api.mvc.Results._


case class TestMeEndpoint(implicit sys: ActorSystem, epc: EndpointRegistryClient) extends DistributedController {
  import concurrent.ExecutionContext.Implicits.global
  import asobu.dsl.DefaultExtractorImplicits._

  case class TestMeReq(n: Int, bar: String)

  handle[TestMeReq]("ep1",
    from(
      fields(bar = header[String]("bar")),
      BodyExtractor.empty
    )) { t =>
    println("got" + t)
    DistributedResult.from(Ok)
  }

}
