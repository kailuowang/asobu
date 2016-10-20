package asobu.distributed

import scala.concurrent.Future

object Util {
  implicit class FutureOps[T](future: Future[T]) {
    import concurrent.ExecutionContext.Implicits.global //operations here should be local
    def void: Future[Unit] = future.map(_ ⇒ ())
  }
}
