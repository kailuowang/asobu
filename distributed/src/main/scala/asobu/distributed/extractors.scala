package asobu.distributed

import _root_.akka.actor.ActorSelection
import asobu.distributed.Action.DistributedRequest
import asobu.distributed.Extractors.RouteParamsExtractor
import asobu.dsl._
import asobu.dsl.util.HListOps.{CombineTo, RestOf2}
import cats.sequence.RecordSequencer
import shapeless.ops.hlist.Prepend
import asobu.dsl.util.RecordOps.{FieldKV, FieldKVs}
import cats.data.Kleisli
import play.api.libs.json.Json
import play.core.routing.RouteParams
import shapeless.labelled.FieldType
import shapeless.ops.hlist.Mapper
import shapeless._, labelled.field
import ExtractResult._
import cats.syntax.all._
import CatsInstances._
import cats.sequence._
import play.api.mvc._, play.api.mvc.Results._

trait Extractors[TMessage] {

  val remoteExtractor: RemoteExtractor

  type LExtracted = remoteExtractor.T

  def localExtract(dr: DistributedRequest[LExtracted]): ExtractResult[TMessage]
}

object Extractors {

  type RouteParamsExtractor[T] = Extractor[RouteParams, T]

  type BodyExtractor[T] = Extractor[AnyContent, T]

  class builder[TMessage](remoteActor0: ActorSelection) {
    def apply[LExtracted <: HList, LParamExtracted <: HList, LExtraExtracted <: HList, LBody <: HList, TRepr <: HList](
      remoteRequestExtractor: RequestExtractor[LExtraExtracted],
      bodyExtractor: BodyExtractor[LBody]
    )(implicit
      gen: LabelledGeneric.Aux[TMessage, TRepr],
      prepend: Prepend.Aux[LParamExtracted, LExtraExtracted, LExtracted],
      r: RestOf2.Aux[TRepr, LExtraExtracted, LBody, LParamExtracted],
      combineTo: CombineTo[LExtracted, LBody, TRepr],
      routeParamsExtractor: RouteParamsExtractor[LParamExtracted]): Extractors[TMessage] = new Extractors[TMessage] {
      val remoteExtractor = RemoteExtractor(routeParamsExtractor, remoteRequestExtractor)

      def localExtract(dr: DistributedRequest[LExtracted]): ExtractResult[TMessage] = ???
    }
  }
}

/**
 * Extract information at the gateway end
 */
trait RemoteExtractor {
  type T
  def apply(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[T]
}

object RemoteExtractor {

  def empty = new RemoteExtractor {
    type T = HNil
    def apply(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[HNil] = ExtractResult.pure(HNil)

  }
  def apply[ParamsRepr <: HList, LExtraExtracted <: HList](
    paramsExtractor: RouteParamsExtractor[ParamsRepr],
    requestExtractor: RequestExtractor[LExtraExtracted]
  )(
    implicit
    prepend: Prepend[ParamsRepr, LExtraExtracted]
  ): RemoteExtractor = new RemoteExtractor {
    type T = prepend.Out
    def apply(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[T] =
      for {
        paramsRepr ← paramsExtractor.run(routeParams)
        requestRepr ← requestExtractor.run(request)
      } yield paramsRepr ++ requestRepr
  }

}

object RouteParamsExtractor {

  val empty = Extractor.empty[RouteParams]

  def apply[T](implicit rpe: RouteParamsExtractor[T]): RouteParamsExtractor[T] = rpe

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
    sequence: RecordSequencer.Aux[KleisliRepr, Kleisli[ExtractResult, RouteParams, Repr]]
  ): RouteParamsExtractor[Repr] = {
    sequence(ks().map(kvToKlesili))
  }
}
