package backend.endpoints

import asobu.distributed.{BodyExtractor, Extractors, Action, Controller}
import asobu.dsl.RequestExtractor
import play.api.mvc._, Results._

import scala.concurrent.Future

object TestMeEndpoint extends Controller {
  case class TestMeReq(n: Int)

  object ep1 extends Action {
    type TMessage = TestMeReq

    val extractors: Extractors[TestMeReq] = Extractors.build[TestMeReq](RequestExtractor.empty, BodyExtractor.empty)

    def backend(t: TestMeReq): Future[Result] = {
      println("got", t)
      Future.successful(Ok)
    }
  }

  val actions = List(ep1)
}
