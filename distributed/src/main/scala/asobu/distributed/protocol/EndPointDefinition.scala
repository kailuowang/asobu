package asobu.distributed.protocol

import akka.actor.{ActorSystem, ActorPath}
import asobu.distributed.RequestEnricherDefinition
import cats.kernel.Order
import play.routes.compiler._
import EndpointDefinition._

@SerialVersionUID(1L)
case class EndpointDefinition(
  verb: Verb,
  path: Seq[PathPart],
  call: String,
  handlerAddress: HandlerPath,
  clusterRole: String,
  enricherDef: Option[RequestEnricherDefinition] = None,
  version: Option[Int] = None
)

/**
 * Represent a set of endpoints a service provides
 *
 * @param prefix path prefix for all endpoints in this set, it's also an unique identifier
 * @param endpoints
 * @param handlerHost
 * @param version
 * @param doc
 */
@SerialVersionUID(1L)
case class EndpointDefinitionSet(
  prefix: Prefix,
  endpoints: Set[EndpointDefinition],
  handlerHost: HandlerHost,
  version: Version,
  doc: Array[Byte]
)

@SerialVersionUID(1L)
case class Sync(set: EndpointDefinitionSet)

@SerialVersionUID(1L)
case class Synced(set: EndpointDefinitionSet)

@SerialVersionUID(1L)
case class Remove(handlerHost: HandlerHost)

@SerialVersionUID(1L)
case class Removed(handlerHost: HandlerHost)

@SerialVersionUID(1L)
case class Verb(value: String)

sealed trait PathPart

@SerialVersionUID(1L)
case class StaticPathPart(value: String) extends PathPart

@SerialVersionUID(1L)
case class Version(major: Int, minor: Int)

@SerialVersionUID(1L)
case class DynamicPathPart(name: String, constraint: String, encode: Boolean) extends PathPart

@SerialVersionUID(1L)
case class HandlerPath(value: String) extends AnyVal

@SerialVersionUID(1L)
case class HandlerHost(value: String) extends AnyVal

@SerialVersionUID(1L)
class Prefix private (val value: String) extends AnyVal

//below are companion objects, more subject to change.

object HandlerHost {
  def from(path: ActorPath): HandlerHost = {
    HandlerHost(path.address.toString)
  }
}

object EndpointDefinition {

  implicit class EndpointDefinitionOps(val ed: EndpointDefinition) {
    import ed._

    lazy val pathPattern = PathPattern(path.map {
      case StaticPathPart(v)        ⇒ StaticPart(v)
      case DynamicPathPart(n, c, e) ⇒ DynamicPart(n, c, e)
    })

    def handlerActorPath = ActorPath.fromString(handlerAddress.value)

    def handlerPath = handlerActorPath.toStringWithoutAddress

  }

  implicit class actorPathToHandlerAddress(actorPath: ActorPath) {
    def handlerAddress: HandlerPath = HandlerPath(actorPath.toStringWithAddress(actorPath.address))
  }

  def apply(
    route: Route,
    handlerPath: HandlerPath,
    clusterRole: String,
    enricherDef: Option[RequestEnricherDefinition]
  ): EndpointDefinition =
    EndpointDefinition(
      Verb(route.verb.value),
      route.path.parts.map {
        case StaticPart(v)        ⇒ StaticPathPart(v)
        case DynamicPart(n, c, e) ⇒ DynamicPathPart(n, c, e)
      },
      route.call.toString(),
      handlerPath,
      clusterRole,
      enricherDef
    )

}

object Prefix {
  val root = apply("/")
  def apply(value: String): Prefix = {
    assert(value.startsWith("/"), "prefix must start with /")
    new Prefix(value)
  }
}

object Version {
  val zero = Version(0, 0)
  implicit val versionOrder: Order[Version] = new Order[Version] {
    override def compare(x: Version, y: Version): Int =
      Array(x.major.compareTo(y.major), x.minor.compareTo(y.minor)).find(_ != 0).getOrElse(0)
  }

  implicit class Ops(val self: Version) extends AnyVal {
    def incrementMinor = self.copy(minor = self.minor + 1)
    def incrementMajor = self.copy(major = self.major + 1, minor = 0)
  }
}
