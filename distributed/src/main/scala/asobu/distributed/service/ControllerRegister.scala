package asobu.distributed.service

import akka.ConfigurationException
import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.util.Timeout
import asobu.distributed.protocol._
import asobu.distributed.{SystemValidator}
import play.api.libs.json.{Json, JsObject}
import play.routes.compiler.{HandlerCall, Route}
import scala.concurrent.{ExecutionContext, Future}

trait ControllerRegister {

  type ApiDocGenerator = (Prefix, Seq[Route]) ⇒ Option[JsObject]
  val voidApiDocGenerator: ApiDocGenerator = (_, _) ⇒ None

  //TODO: don't have implicits for all these arguments
  def init(prefix: Prefix)(controllers: Controller*)(
    implicit
    ec: ExecutionContext,
    system: ActorSystem,
    ao: Timeout,
    version: Version,
    apiDocGenerator: ApiDocGenerator = voidApiDocGenerator
  ): Future[EndpointDefinitionSet] = {
    //    val registry: EndpointsRegistry = DefaultEndpointsRegistry(system)
    //    val rec: EndpointsRegistryClient = EndpointsRegistryClientImp(registry)

    def endpointsOf(controller: Controller): Seq[EndpointDefinition] = {
      def findRoute(action: Action): Route = controller.routes.find { r ⇒
        val HandlerCall(packageName, controllerName, _, method, _) = r.call
        action.name == packageName + "." + controllerName + "." + method
      }.getOrElse {
        throw new Exception(s"Cannot find route for action ${action.name}") //todo: this should really be a compilation error, the next right thing to do is to let it blow up the application on start.
      }

      controller.actions.map {
        action ⇒ action.endpointDefinition(findRoute(action))
      }
    }

    SystemValidator.validate(system) match {
      case Left(error) ⇒ Future.failed(new ConfigurationException(error))
      case _ ⇒
        val endpointsL = controllers.flatMap(endpointsOf)
        val endpoints = endpointsL.toSet
        assert(endpoints.size == endpointsL.length, "There are duplicated endpoints defined.") //todo better error handling here
        val doc = apiDocGenerator(prefix, controllers.flatMap(_.routes)).fold(Array[Byte]())(Json.stringify(_).getBytes)

        Future.successful(EndpointDefinitionSet(
          prefix,
          endpoints,
          HandlerHost(Cluster(system).selfAddress.toString),
          version,
          doc
        ))
      //        rec.add(EndpointDefinitionSet(
      //          prefix,
      //          endpoints,
      //          HandlerHost(Cluster(system).selfAddress.toString),
      //          version,
      //          doc
      //        ))
    }
  }

}
