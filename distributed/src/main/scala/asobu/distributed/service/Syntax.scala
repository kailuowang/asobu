package asobu.distributed.service

import _root_.akka.actor.{ActorSystem, ActorRef}
import _root_.akka.util.Timeout
import Action.DistributedResult
import asobu.distributed.service.Syntax.Processor
import asobu.distributed.{RequestExtractorDefinition, EndpointDefinition}

import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Results, Result}
import play.api.mvc.Results._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._
import _root_.akka.pattern.ask
import Syntax._
import asobu.dsl.CatsInstances._
import scala.concurrent.ExecutionContext.Implicits.global

trait Syntax {
  self: Controller ⇒
  def actionName(name: String) = getClass.getName.stripSuffix("$").replace('$', '.') + "." + name

  def handle[T](
    name0: String,
    extrs: Extractors[T]
  )(bk: T ⇒ Future[DistributedResult])(
    implicit
    rc: EndpointsRegistryClient, ec: ExecutionContext, sys: ActorSystem
  ): Future[EndpointDefinition] = {
    val action = new Action {
      val name = actionName(name0)
      type TMessage = T
      val extractors = extrs
      def backend(t: T) = bk(t)
    }
    addAction(action)
  }

  def from = RequestExtractorDefinition.compose

  val noExtraFields = RequestExtractorDefinition.empty

  def process[T] = Extractors.build[T]

  def using(actor: ActorRef)(implicit at: Timeout, ec: ExecutionContext): Processor[Any, Any] =
    Processor.fromFunction { t ⇒
      ProcessResult.right(actor ? t)
    }

  implicit class ProcessorOps[A, B](self: Processor[A, B]) {
    def expect[ResponseT: ClassTag]: Processor[A, ResponseT] = {
      self.flatMapF {
        case t: ResponseT if classTag[ResponseT].runtimeClass.isInstance(t) ⇒ ProcessResult.pure(t)
        case _ ⇒ ProcessResult.left(InternalServerError(s"unexpected response from backend, was expecting ${classTag[ResponseT].runtimeClass.getTypeName}")) //todo: globalizing un expected result from backend error
      }
    }

    def respond(result: Result): Processor[A, Result] = respond(_ ⇒ result)

    def respond(f: B ⇒ Result): Processor[A, Result] = self.map(f)

    def respondJson(r: Results#Status)(implicit writes: Writes[B]): Processor[A, Result] =
      self.map(t ⇒ r.apply(Json.toJson(t)))
  }

  implicit def toBackend[T](processor: Processor[T, Result]): T ⇒ Future[DistributedResult] =
    (t: T) ⇒ processor.run(t).v.fold(identity, identity).flatMap(DistributedResult.from(_))

}

object Syntax {
  import asobu.dsl.{Extractor, ExtractResult}
  /**
   * Reuse the [[Extractor]] type but not in the extraction context
   *
   * @tparam TIn
   * @tparam TOut
   */
  type Processor[TIn, TOut] = Extractor[TIn, TOut]
  val Processor = Extractor
  val ProcessResult = ExtractResult
}
