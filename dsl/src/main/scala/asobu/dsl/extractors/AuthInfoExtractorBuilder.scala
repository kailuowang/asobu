package asobu.dsl.extractors

import cats.data.Kleisli
import asobu.dsl.{ExtractResult, Extractor}
import ExtractResult._
import cats.sequence.RecordSequencer
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import shapeless._
import shapeless.ops.record.Selector; import record._
import scala.concurrent.Future
import asobu.dsl.CatsInstances._

class AuthInfoExtractorBuilder[AuthInfoT](buildAuthInfo: RequestHeader ⇒ Future[Either[String, AuthInfoT]]) {

  def apply[Repr <: HList](toRecord: AuthInfoT ⇒ Repr): Extractor[Repr] =
    apply().map(toRecord)

  def apply(): Extractor[AuthInfoT] = Kleisli(
    buildAuthInfo.andThen(_.map(_.left.map(Unauthorized(_)))).andThen(fromEither)
  )

  def field[K <: Symbol, Repr <: HList](key: Witness.Aux[K])(
    implicit
    gen: LabelledGeneric.Aux[AuthInfoT, Repr],
    select: Selector[Repr, K]

  ): Extractor[select.Out] =
    apply().map { ai ⇒
      gen.to(ai)(key)
    }

  object from extends RecordArgs {
    def applyRecord[Repr <: HList, Out <: HList](repr: Repr)(
      implicit
      seq: RecordSequencer.Aux[Repr, AuthInfoT ⇒ Out]
    ): Extractor[Out] = {
      apply(seq(repr))
    }
  }
}
