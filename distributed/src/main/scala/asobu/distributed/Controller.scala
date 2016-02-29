package asobu.distributed

import akka.actor.ActorRefFactory
import asobu.distributed.Endpoint.Prefix
import play.routes.compiler.{HandlerCall, Route}

trait Controller {

  def prefix = Prefix("/")

  val routes: List[Route] = EndpointParser.parseResource(prefix).right.get

  private def findRoute(action: Action): Route = routes.find { r ⇒
    val HandlerCall(packageName, controllerName, _, method, _) = r.call
    action.name == packageName + "." + controllerName + "." + method
  }.getOrElse(throw new Exception(s"Cannot find route for action ${action.name}"))

  def actions: List[Action]

  def endpointDefs(implicit arf: ActorRefFactory): List[EndpointDefinition] = actions.map { action ⇒
    action.endpointDefinition(findRoute(action), prefix)
  }
}
