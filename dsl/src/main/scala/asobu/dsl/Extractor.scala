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
  val empty: Extractor[HNil] = apply(_ ⇒ HNil)

  def apply[T](f: Request[AnyContent] ⇒ T): Extractor[T] = f map pure

  implicit def fromFunction[T](f: Request[AnyContent] ⇒ ExtractResult[T]): Extractor[T] = Kleisli(f)

  implicit def fromFunctionXorT[T](f: Request[AnyContent] ⇒ XorTF[T]): Extractor[T] = f.andThen(ExtractResult(_))

}

trait ExtractorBuilderSyntax {

  /**
   * extractor from a list of functions
   * e.g. from(a = (_:Request[AnyContent]).headers("aKay"))
   */
  object from extends shapeless.RecordArgs {
    def applyRecord[Repr <: HList, Out <: HList](repr: Repr)(
      implicit
      seq: RecordSequencer.Aux[Repr, Request[AnyContent] ⇒ Out]
    ): Extractor[Out] = {
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
    def applyRecord[Repr <: HList, Out <: HList](repr: Repr)(
      implicit
      seq: RecordSequencer.Aux[Repr, Extractor[Out]]
    ): Extractor[Out] = {
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

  implicit class extractorOps[Repr <: HList](self: Extractor[Repr]) {
    def and[ThatR <: HList, ResultR <: HList](that: Extractor[ThatR])(
      implicit
      prepend: Prepend.Aux[Repr, ThatR, ResultR]
    ): Extractor[ResultR] = { (req: Request[AnyContent]) ⇒

      for {
        eitherRepr ← self.run(req)
        eitherThatR ← that.run(req)
      } yield eitherRepr ++ eitherThatR
    }
  }

}

