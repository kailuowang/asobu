package asobu.distributed

import _root_.akka.actor.ActorSelection
import asobu.distributed.Action.DistributedRequest
import asobu.distributed.Extractors.{RemoteExtractor, BodyExtractor, RouteParamsExtractor}
import asobu.dsl._
import asobu.dsl.extractors.JsonBodyExtractor
import asobu.dsl.util.HListOps.{CombineTo, RestOf2}
import cats.sequence.RecordSequencer
import shapeless.ops.hlist.Prepend
import asobu.dsl.util.RecordOps.{FieldKV, FieldKVs}
import cats.data.Kleisli
import play.api.libs.json.{Reads, Json}
import play.core.routing.RouteParams
import shapeless.labelled.FieldType
import shapeless.ops.hlist.Mapper
import shapeless._, labelled.field
import ExtractResult._
import cats.syntax.all._
import CatsInstances._
import cats.sequence._
import play.api.mvc._, play.api.mvc.Results._

import scala.annotation.implicitNotFound

trait Extractors[TMessage] {
  type LExtracted <: HList

  val remoteExtractor: RemoteExtractor[LExtracted]

  def localExtract(dr: DistributedRequest[LExtracted]): ExtractResult[TMessage]
}

object Extractors {
  type Aux[TMessage, LExtracted0] = Extractors[TMessage] { type LExtracted = LExtracted0 }

  type RouteParamsExtractor[T] = Extractor[RouteParams, T]

  /**
   * Extract information at the gateway end
   */
  type RemoteExtractor[T] = Extractor[(RouteParams, Request[AnyContent]), T]

  type BodyExtractor[T] = Extractor[AnyContent, T]

  class builder[TMessage] {
    def apply[LExtracted0 <: HList, LParamExtracted <: HList, LRemoteExtra <: HList, LBody <: HList, TRepr <: HList](
      remoteRequestExtractor: RequestExtractor[LRemoteExtra],
      bodyExtractor: BodyExtractor[LBody]
    )(implicit
      gen: LabelledGeneric.Aux[TMessage, TRepr],
      r: RestOf2.Aux[TRepr, LRemoteExtra, LBody, LParamExtracted],
      prepend: Prepend.Aux[LParamExtracted, LRemoteExtra, LExtracted0],
      combineTo: CombineTo[LExtracted0, LBody, TRepr],
      rpeb: RouteParamsExtractorBuilder[LParamExtracted]): Aux[TMessage, LExtracted0] = new Extractors[TMessage] {

      type LExtracted = LExtracted0

      val remoteExtractor = Extractor.combine(rpeb(), remoteRequestExtractor)

      def localExtract(dr: DistributedRequest[LExtracted]): ExtractResult[TMessage] = bodyExtractor.run(dr.body).map { body ⇒
        val repr = combineTo(dr.extracted, body)
        gen.from(repr)
      }
    }
  }
  //todo: the RouteParamsExtractor will be automatically derived from the TMessage bodyExtractor and remoteRequestExtractor, maybe an explicit version will be helpful.
  def build[TMessage] = new builder[TMessage]
}

object BodyExtractor {
  val empty = Extractor.empty[AnyContent]
  def json[T: Reads]: BodyExtractor[T] = Extractor.fromFunction(JsonBodyExtractor.extractBody[T])
  def jsonList[T: Reads](implicit lgen: LabelledGeneric[T]): BodyExtractor[lgen.Repr] =
    json[T] map (lgen.to(_))

}

object RemoteExtractor {
  val empty = Extractor.empty[(RouteParams, Request[AnyContent])]
}

object RouteParamsExtractor {
  def apply[L <: HList](implicit builder: RouteParamsExtractorBuilder[L]): RouteParamsExtractor[L] = builder()
}

@implicitNotFound("Cannot construct RouteParamsExtractor out of ${L}")
trait RouteParamsExtractorBuilder[L <: HList] extends (() ⇒ RouteParamsExtractor[L])

trait MkRouteParamsExtractorBuilder0 {

  //todo: this extract from either path or query without a way to specify one way or another.
  object kvToKlesili extends Poly1 {
    implicit def caseKV[K <: Symbol, V: PathBindable: QueryStringBindable](
      implicit
      w: Witness.Aux[K]
    ): Case.Aux[FieldKV[K, V], FieldType[K, Kleisli[ExtractResult, RouteParams, V]]] =
      at[FieldKV[K, V]] { kv ⇒
        field[K](Kleisli { (params: RouteParams) ⇒
          val field: String = w.value.name
          val xor = params.fromPath[V](field).value.toXor orElse params.fromQuery[V](field).value.toXor

          fromXor(xor.leftMap(m ⇒ BadRequest(Json.obj("error" → s"missing field $field $m"))))
        })
      }
  }

  implicit def autoMkForRecord[Repr <: HList, KVs <: HList, KleisliRepr <: HList](
    implicit
    ks: FieldKVs.Aux[Repr, KVs],
    mapper: Mapper.Aux[kvToKlesili.type, KVs, KleisliRepr],
    sequence: RecordSequencer[KleisliRepr]
  ): RouteParamsExtractorBuilder[Repr] = new RouteParamsExtractorBuilder[Repr] {
    def apply(): RouteParamsExtractor[Repr] =
      sequence(ks().map(kvToKlesili)).asInstanceOf[Kleisli[ExtractResult, RouteParams, Repr]] //this cast is needed because a RecordSequencer.Aux can't find the Repr if Repr is not explicitly defined. The cast is safe because the mapper guarantee the result type. todo: find a way to get rid of the cast
  }
}

object RouteParamsExtractorBuilder extends MkRouteParamsExtractorBuilder0 {

  def apply[T <: HList](implicit rpe: RouteParamsExtractorBuilder[T]): RouteParamsExtractorBuilder[T] = rpe

  implicit val empty: RouteParamsExtractorBuilder[HNil] = new RouteParamsExtractorBuilder[HNil] {
    def apply() = Extractor.empty[RouteParams]
  }
}
