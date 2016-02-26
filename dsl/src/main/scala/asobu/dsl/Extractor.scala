package asobu.dsl

import cats.data.{Kleisli, Xor, XorT}
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request, Result}
import shapeless.ops.hlist._
import shapeless._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import ExtractResult._
import cats.syntax.all._
import CatsInstances._
import cats.sequence._

object Extractor extends ExtractorBuilderSyntax {
  def empty[TFrom]: Extractor[TFrom, HNil] = apply(_ ⇒ HNil)

  def apply[TFrom, T](f: TFrom ⇒ T): Extractor[TFrom, T] = f map pure

  implicit def fromFunction[TFrom, T](f: TFrom ⇒ ExtractResult[T]): Extractor[TFrom, T] = Kleisli(f)

  implicit def fromFunctionXorT[TFrom, T](f: TFrom ⇒ XorTF[T]): Extractor[TFrom, T] = f.andThen(ExtractResult(_))

}

object RequestExtractor {
  val empty = Extractor.empty[Request[AnyContent]]
  def apply[T](f: Request[AnyContent] ⇒ T): RequestExtractor[T] = Extractor(f)
}

trait ExtractorBuilderSyntax {

  /**
   * extractor from a list of functions
   * e.g. from(a = (_:Request[AnyContent]).headers("aKay"))
   */
  object from extends shapeless.RecordArgs {
    def applyRecord[TFrom, Repr <: HList, Out <: HList](repr: Repr)(
      implicit
      seq: RecordSequencer.Aux[Repr, TFrom ⇒ Out]
    ): Extractor[TFrom, Out] = {
      Extractor(seq(repr))
    }
  }

  /**
   * extractor composed of several extractors
   * e.g. compose(a = Extractor(_.headers("aKey"))
   * or
   * compose(a = header("aKey"))  //header is method that constructor a more robust Extractor
   */
  object compose extends shapeless.RecordArgs {
    def applyRecord[TFrom, Repr <: HList, Out <: HList](repr: Repr)(
      implicit
      seq: RecordSequencer.Aux[Repr, Extractor[TFrom, Out]]
    ): Extractor[TFrom, Out] = {
      seq(repr)
    }
  }
}

trait DefaultExtractorImplicits {
  implicit val ifFailure: FallbackResult = e ⇒ BadRequest(e.getMessage)
}

object DefaultExtractorImplicits extends DefaultExtractorImplicits

trait ExtractorOps {
  import Extractor._

  implicit class extractorOps[TFrom, Repr <: HList](self: Extractor[TFrom, Repr]) {
    def and[ThatR <: HList, ResultR <: HList](that: Extractor[TFrom, ThatR])(
      implicit
      prepend: Prepend.Aux[Repr, ThatR, ResultR]
    ): Extractor[TFrom, ResultR] = { (req: TFrom) ⇒

      for {
        eitherRepr ← self.run(req)
        eitherThatR ← that.run(req)
      } yield eitherRepr ++ eitherThatR
    }
  }

}

