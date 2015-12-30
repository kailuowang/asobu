package asobu.dsl.extractors

import cats.data.{Kleisli, XorT, Xor}
import asobu.dsl.{ExtractResult, Extractor}
import ExtractResult._
import cats.sequence.RecordSequencer
import play.api.mvc.{Result, RequestHeader}
import play.api.mvc.Results._
import shapeless._; import syntax.singleton._; import record._; import labelled.{FieldType, field}
import scala.concurrent.Future
import asobu.dsl.CatsInstances._

class AuthInfoExtractorBuilder[AuthInfoT](buildAuthInfo: RequestHeader ⇒ Future[Either[String, AuthInfoT]]) {

  def apply[Repr <: HList](toRecord: AuthInfoT ⇒ Repr): Extractor[Repr] =
    apply().map(toRecord)

  def apply(): Extractor[AuthInfoT] = Kleisli(
    buildAuthInfo.andThen(_.map(_.left.map(Unauthorized(_)))).andThen(fromEither)
  )

  object from extends RecordArgs {
    def applyRecord[Repr <: HList, Out <: HList](repr: Repr)(
      implicit
      seq: RecordSequencer.Aux[Repr, AuthInfoT ⇒ Out]
    ): Extractor[Out] = {
      apply(seq(repr))
    }
  }
}
