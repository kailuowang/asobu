package asobu.distributed

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.test.FakeRequest
import asobu.dsl.CatsInstances._
import concurrent.ExecutionContext.Implicits.global

import shapeless._
import shapeless.record.Record
class RequestExtractorDefinitionSpec extends Specification {
  import RequestExtractorDefinition._
  "can compose" >> { implicit ev: ExecutionEnv ⇒
    import asobu.dsl.DefaultExtractorImplicits._
    val re = compose(a = (header[String]("big"): RequestExtractorDefinition[String]))
    val extractor = re.apply()

    val result = extractor.run(FakeRequest().withHeaders("big" → "lala"))

    result.getOrElse(null) must be_==(Record(a = "lala")).await

  }

}
