package asobu.dsl.util

import shapeless.{::, HNil, HList}
import shapeless.ops.hlist.{Remove, ZipWithKeys, Align, Prepend}
import shapeless.ops.record.{Values, Keys}

import scala.annotation.implicitNotFound

object HListOps {

  trait CombineTo[R1 <: HList, R2 <: HList, Out <: HList] {
    def apply(r1: R1, r2: R2): Out
  }

  object CombineTo {
    implicit def apply[R1 <: HList, R2 <: HList, Out <: HList, TempFull <: HList](
      implicit
      prepend: Prepend.Aux[R1, R2, TempFull],
      align: Align[TempFull, Out]
    ): CombineTo[R1, R2, Out] = new CombineTo[R1, R2, Out] {
      def apply(r1: R1, r2: R2): Out = align(prepend(r1, r2))
    }
  }

  /**
   * Attach keys from the record Out onto the values of HList L to construct a record of Out
   *
   * @tparam L
   * @tparam Out
   */
  @implicitNotFound("Cannot transfer ${L} into ${Out}, incompatible types.")
  trait ToRecord[L <: HList, Out <: HList] {
    def apply(l: L): Out
  }

  object ToRecord {
    implicit def apply[L <: HList, Out <: HList, K <: HList](
      implicit
      keys: Keys.Aux[Out, K],
      v: Values.Aux[Out, L],
      zip: ZipWithKeys.Aux[K, L, Out]
    ): ToRecord[L, Out] = new ToRecord[L, Out] {
      def apply(l: L): Out = l.zipWithKeys[K]
    }
  }

  trait RestOf[L <: HList, SL <: HList] {
    type Out <: HList
  }

  object RestOf {

    type Aux[L <: HList, SL <: HList, Out0 <: HList] = RestOf[L, SL] {
      type Out = Out0
    }

    implicit def hlistRestOfNil[L <: HList]: Aux[L, HNil, L] = new RestOf[L, HNil] { type Out = L }

    implicit def hlistRestOf[L <: HList, E, RemE <: HList, Rem <: HList, SLT <: HList](implicit rt: Remove.Aux[L, E, (E, RemE)], st: Aux[RemE, SLT, Rem]): Aux[L, E :: SLT, Rem] =
      new RestOf[L, E :: SLT] { type Out = Rem }
  }
}
