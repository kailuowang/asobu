package asobu.distributed

import asobu.dsl.ExtractResult._
import asobu.dsl._
import asobu.dsl.extractors.HeaderExtractors
import asobu.dsl.util.Read
import cats.{Apply, Applicative, Functor}
import cats.sequence.RecordSequencer
import shapeless.ops.hlist._
import shapeless._

/**
 * Isolates extractor definition from actual extraction logic, will allow api to be more binary compatible
 *
 * @tparam T
 */
trait RequestExtractorDefinition[T] extends Serializable {
  def apply(): RequestExtractor[T]
}

object RequestExtractorDefinition extends PredefinedDefs {
  import LocalExecutionContext.instance //this always happens locally, and needs to be serializable

  implicit def app: Applicative[RequestExtractorDefinition] =
    new Applicative[RequestExtractorDefinition] {

      val appR = Applicative[RequestExtractor]

      def ap[A, B](ff: RequestExtractorDefinition[(A) ⇒ B])(fa: RequestExtractorDefinition[A]): RequestExtractorDefinition[B] = new RequestExtractorDefinition[B] {
        def apply = appR.ap(ff.apply())(fa.apply())
      }

      def product[A, B](
        fa: RequestExtractorDefinition[A],
        fb: RequestExtractorDefinition[B]
      ): RequestExtractorDefinition[(A, B)] = new RequestExtractorDefinition[(A, B)] {
        def apply(): RequestExtractor[(A, B)] = appR.product(fa.apply(), fb.apply())
      }

      def map[A, B](fa: RequestExtractorDefinition[A])(f: (A) ⇒ B): RequestExtractorDefinition[B] = new RequestExtractorDefinition[B] {
        def apply(): RequestExtractor[B] = fa.apply().map(f)
      }

      def pure[A](x: A): RequestExtractorDefinition[A] = new RequestExtractorDefinition[A] {
        def apply(): RequestExtractor[A] = appR.pure(x)
      }

    }

  val empty: RequestExtractorDefinition[HNil] = new RequestExtractorDefinition[HNil] {
    def apply = RequestExtractor.empty
  }

  object compose extends shapeless.RecordArgs {
    def applyRecord[TFrom, Repr <: HList, Out <: HList](repr: Repr)(
      implicit
      seq: RecordSequencer.Aux[Repr, RequestExtractorDefinition[Out]]
    ): RequestExtractorDefinition[Out] = {
      seq(repr)
    }
  }

  /**
   * combine two extractors into one that returns a concated list of the two results
   *
   * @return
   */
  def combine[LA <: HList, LB <: HList, LOut <: HList](
    ea: RequestExtractorDefinition[LA],
    eb: RequestExtractorDefinition[LB]
  )(
    implicit
    prepend: Prepend.Aux[LA, LB, LOut]
  ): RequestExtractorDefinition[LOut] = new RequestExtractorDefinition[LOut] {
    def apply: RequestExtractor[LOut] = Extractor.combine(ea(), eb())
  }

}

object PredefinedDefs {
  @SerialVersionUID(1L)
  case class Header[T: Read](key: String)(implicit fbr: FallbackResult) extends RequestExtractorDefinition[T] {
    import concurrent.ExecutionContext.Implicits.global
    def apply() = HeaderExtractors.header(key)
  }
}

trait PredefinedDefs {
  import PredefinedDefs._
  def header[T: Read](key: String)(implicit fbr: FallbackResult): RequestExtractorDefinition[T] = Header[T](key)
}

