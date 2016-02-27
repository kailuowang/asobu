package asobu.distributed

import asobu.dsl.{RequestExtractor, Extractor}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import Extractor._
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc.{Request, AnyContent}
import play.api.test.FakeRequest
import play.core.routing.RouteParams
import shapeless._
import shapeless.record.Record
import asobu.dsl.CatsInstances._

object ExtractorsSpec extends Specification {
  case class MyMessage(foo: String, bar: Int, bar2: Boolean)
  case class MyMessageBody(bar: Int)

  implicit val f = Json.format[MyMessageBody]

  "can build extractors without routesParams to extract" >> { implicit ev: ExecutionEnv ⇒
    val reqExtractor = compose(foo = RequestExtractor(_.headers("foo_h")), bar2 = RequestExtractor(_.headers("bar2").toBoolean))
    val bodyExtractor = BodyExtractor.jsonList[MyMessageBody]
    val extractors = Extractors.build[MyMessage](reqExtractor, bodyExtractor)

    val params = RouteParams(Map.empty, Map.empty)
    val req: Request[AnyContent] = FakeRequest().withJsonBody(Json.obj("bar" → JsNumber(3))).withHeaders("foo_h" → "foV", "bar2" → "true")

    extractors.remoteExtractor.run((params, req)).getOrElse(null) must be_==("foV" :: true :: HNil).await

  }

  "can build extractor correctly with routesParams to extract" >> { implicit ev: ExecutionEnv ⇒
    val reqExtractor = compose(foo = RequestExtractor(_.headers("foo_h")))
    val bodyExtractor = BodyExtractor.jsonList[MyMessageBody]
    val extractors = Extractors.build[MyMessage](reqExtractor, bodyExtractor)

    val params = RouteParams(Map.empty, Map("bar2" → Seq("true")))
    val req: Request[AnyContent] = FakeRequest().withJsonBody(Json.obj("bar" → JsNumber(3))).withHeaders("foo_h" → "foV")

    extractors.remoteExtractor.run((params, req)).getOrElse(null) must be_==(true :: "foV" :: HNil).await

  }
}
