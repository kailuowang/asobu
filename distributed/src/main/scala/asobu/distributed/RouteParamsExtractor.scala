package asobu.distributed

import asobu.dsl._
import asobu.dsl.util.Read
import asobu.dsl.util.RecordOps.{FieldKV, FieldKVs}
import cats.data.Kleisli
import play.api.libs.json.Json
import play.api.mvc.{QueryStringBindable, PathBindable, AnyContent, Request}
import play.core.routing.RouteParams
import shapeless.labelled.FieldType
import shapeless.ops.hlist.Mapper
import shapeless.ops.record.Keys
import shapeless._, labelled.field
import ExtractResult._
import cats.syntax.all._
import CatsInstances._
import cats.sequence._
import play.api.mvc._, Results._

object RouteParamsExtractor {

  val empty: RouteParamsExtractor[HNil] = apply(_ ⇒ HNil)

  def apply[T](implicit rpe: RouteParamsExtractor[T]): RouteParamsExtractor[T] = rpe

  def apply[T](f: RouteParams ⇒ T): RouteParamsExtractor[T] = f map pure

  implicit def fromFunction[T](f: RouteParams ⇒ ExtractResult[T]): RouteParamsExtractor[T] = Kleisli(f)

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
