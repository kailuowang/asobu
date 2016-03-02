package backend.endpoints

import asobu.distributed.Action.DistributedResult
import asobu.distributed.{BodyExtractor, Extractors, Action, Controller}
import asobu.dsl.RequestExtractor
import asobu.dsl.extractors.HeaderExtractors
import play.api.mvc._, Results._

import scala.concurrent.Future

object TestMeEndpoint extends Controller {
  import HeaderExtractors.header
  import asobu.dsl.Extractor.compose
  import asobu.dsl.DefaultExtractorImplicits._

  case class TestMeReq(n: Int, bar: String)

  object ep1 extends Action {
    type TMessage = TestMeReq

    val extractors: Extractors[TestMeReq] = Extractors.build[TestMeReq](compose(bar = header[String]("bar")), BodyExtractor.empty)

    def backend(t: TestMeReq): Future[DistributedResult] = {
      println("got" + t)
      DistributedResult.from(Ok)
    }
  }

  val actions = List(ep1)
}
