package asobu.distributed

import akka.actor.{ActorSystem, ActorRefFactory}
import asobu.distributed.Endpoint.Prefix
import play.routes.compiler.{HandlerCall, Route}

import scala.concurrent.{ExecutionContext, Future}

trait DistributedController extends Controller with Syntax with PredefinedDefs

trait Controller {
  /**
   * Used to get route file "$name.route"
   *
   * @return
   */
  def name: String = getClass.getSimpleName.stripSuffix("$")
  def prefix = Prefix("/")

  lazy val routes: List[Route] = EndpointParser.parseResource(prefix, s"$name.routes") match {
    case Right(rs) ⇒ rs
    case Left(err) ⇒ throw RoutesParsingException(err.map(_.toString).mkString(". "))
  }

  private def findRoute(action: Action): Route = routes.find { r ⇒
    val HandlerCall(packageName, controllerName, _, method, _) = r.call
    action.name == packageName + "." + controllerName + "." + method
  }.getOrElse(throw new Exception(s"Cannot find route for action ${action.name}"))

  def addAction(action: Action)(
    implicit
    registryClient: EndpointRegistryClient,
    ec: ExecutionContext,
    sys: ActorSystem
  ): Future[EndpointDefinition] = {
    val epd: EndpointDefinition = toEndPointDefinition(action)
    registryClient.add(epd).map(_ ⇒ epd)
  }

  protected def toEndPointDefinition(action: Action)(implicit sys: ActorSystem): EndpointDefinition =
    action.endpointDefinition(findRoute(action), prefix)

}

