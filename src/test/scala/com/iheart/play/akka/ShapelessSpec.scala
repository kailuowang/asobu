package com.iheart.play.akka

import org.specs2.mutable.Specification
import shapeless._
import ops.hlist._
import syntax.singleton._

object Test {
  case class RawReq(ip: String, header: Map[String, String] = Map())

  case class Result(status: String, body: String)

  type Directive[T] = T ⇒ Result

  case class RequestMessage(name: String, ipAllowed: Boolean, userId: String)

  trait RestOf[L <: HList, SL <: HList] {
    type Out <: HList
  }

  object RestOf {

    type Aux[L <: HList, SL <: HList, Out0 <: HList] = RestOf[L, SL] {
      type Out = Out0
    }

    implicit def hlistRestOfNil[L <: HList]: Aux[L, HNil, L] = new RestOf[L, HNil] { type Out = L }

    implicit def hlistRestOf[L <: HList, E, RemE <: HList, Rem <: HList, SLT <: HList]
      (implicit rt: Remove.Aux[L, E, (E, RemE)], st: Aux[RemE, SLT, Rem]): Aux[L, E :: SLT, Rem] =
      new RestOf[L, E :: SLT] { type Out = Rem }
  }

  class Convert[L <: HList, T](f: L ⇒ T) extends RecordArgs {
    def applyRecord(l: L): T = f(l)
  }

  class PartialHandlerConstructor[T, Repr <: HList, ExtractedRepr <: HList, InputRepr <: HList]
    (extractor: RawReq ⇒ ExtractedRepr)
    (implicit lgen: LabelledGeneric.Aux[T, Repr]) {
    def apply[TempFull <: HList](dir: Directive[T])
      (implicit prepend: Prepend.Aux[InputRepr, ExtractedRepr, TempFull],
      align: Align[TempFull, Repr]): InputRepr ⇒ (RawReq ⇒ Result) =
        (inputRepr: InputRepr) ⇒ (raw: RawReq) ⇒ {
          dir(lgen.from(align(inputRepr ++ extractor(raw))))
        }
  }

  class HandlerConstructor[T]() {
    def apply[Repr <: HList,ExtractedRepr <: HList, InputRepr <: HList]
    (extractor: RawReq ⇒ ExtractedRepr)
    (implicit lgen: LabelledGeneric.Aux[T, Repr],
     restOf: RestOf.Aux[Repr, ExtractedRepr, InputRepr]) = {
      new PartialHandlerConstructor[T, Repr, ExtractedRepr, InputRepr](extractor)
    }
  }

}

class HandlerSpec extends Specification {
  import Test._


  "handler" should {
    "generate action functions" in {
      val hc = new HandlerConstructor[RequestMessage]

      val handler1 = hc((r: RawReq) ⇒ ('ipAllowed ->> (r.ip.length > 3)) :: HNil)

      val dir: Directive[RequestMessage] =
        (rm: RequestMessage) ⇒ Result(rm.ipAllowed.toString, rm.name)

      val action = handler1(dir)

      val converted = new Convert(action.apply)

      val result = converted(name = "big", userId = "aId")


      result(RawReq("anewiP")) === Result("true", "big")
    }
  }
}

