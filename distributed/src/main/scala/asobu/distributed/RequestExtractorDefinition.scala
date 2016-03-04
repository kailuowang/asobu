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
trait RequestExtractorDefinition[T] extends (() ⇒ RequestExtractor[T]) with Serializable

object RequestExtractorDefinition extends PredefinedDefs {
  import LocalExecutionContext.instance //this always happens locally, and needs to be serializable

  implicit def app: Applicative[RequestExtractorDefinition] =
    new Applicative[RequestExtractorDefinition] {

      val appR = Applicative[RequestExtractor]

      def ap[A, B](ff: RequestExtractorDefinition[(A) ⇒ B])(fa: RequestExtractorDefinition[A]) =
        new RequestExtractorDefinition[B] {
          def apply = appR.ap(ff())(fa())
        }

      def product[A, B](
        fa: RequestExtractorDefinition[A],
        fb: RequestExtractorDefinition[B]
      ) = new RequestExtractorDefinition[(A, B)] {
        def apply(): RequestExtractor[(A, B)] = appR.product(fa(), fb())
      }

      def map[A, B](fa: RequestExtractorDefinition[A])(f: (A) ⇒ B) = new RequestExtractorDefinition[B] {
        def apply(): RequestExtractor[B] = fa().map(f)
      }

      def pure[A](x: A) = new RequestExtractorDefinition[A] {
        def apply(): RequestExtractor[A] = appR.pure(x)
      }

    }

  val empty: RequestExtractorDefinition[HNil] = new RequestExtractorDefinition[HNil] {
    def apply = RequestExtractor.empty
  }

  def compose = cats.sequence.sequenceRecord

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

