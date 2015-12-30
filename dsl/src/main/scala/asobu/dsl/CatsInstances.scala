package asobu.dsl

import cats.functor.Contravariant

trait CatsInstances extends cats.std.AllInstances {
  implicit val ex = scala.concurrent.ExecutionContext.Implicits.global

  implicit def partialFunctionContravariant[R]: Contravariant[PartialFunction[?, R]] =
    new Contravariant[PartialFunction[?, R]] {
      def contramap[T1, T0](pa: PartialFunction[T1, R])(f: T0 ⇒ T1) = new PartialFunction[T0, R] {
        def isDefinedAt(x: T0): Boolean = pa.isDefinedAt(f(x))
        def apply(x: T0): R = pa(f(x))
      }
    }

}

object CatsInstances extends CatsInstances

