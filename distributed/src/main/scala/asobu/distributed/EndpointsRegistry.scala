//package asobu.distributed
//
//import akka.actor._
//import akka.cluster.{Member, MemberStatus, Cluster}
//import akka.cluster.ddata.{LWWMap, LWWMapKey, DistributedData}
//import akka.cluster.ddata.Replicator._
//import asobu.distributed.EndpointsRegistry.{EndpointDefinitionSets, DocDataType}
//import asobu.distributed.gateway.enricher.Interpreter
//import asobu.distributed.protocol.{HandlerHost, EndpointDefinitionSet, EndpointDefinition}
//import play.api.libs.json.{JsNumber, Json, JsObject}
//import concurrent.duration._
//
//trait EndpointsRegistry {
//
//  def writeConsistency: WriteConsistency
//  def readConsistency: ReadConsistency
//  val EndpointsDataKey = LWWMapKey[EndpointDefinitionSets]("endpoints-registry-endpoints")
//
//  def emptyDocs: LWWMap[DocDataType]
//  val emptyData = LWWMap.empty[EndpointDefinitionSets]
//
//  implicit def node: Cluster
//  def replicator: ActorRef
//}
//
//object EndpointsRegistry {
//
//  //Doc json is stored as String (JsObject isn't that serializable after manipulation)
//  type DocDataType = String
//  type EndpointDefinitionSets = Set[EndpointDefinitionSet]
//}
//
//case class DefaultEndpointsRegistry(system: ActorSystem) extends EndpointsRegistry {
//  val timeout = 30.seconds
//
//  val writeConsistency = WriteAll(timeout)
//  val readConsistency = ReadMajority(timeout)
//
//  implicit val node = Cluster(system)
//  val replicator: ActorRef = DistributedData(system).replicator
//}
//
//class EndpointsRegistryUpdater(registry: EndpointsRegistry) extends Actor with ActorLogging {
//
//  import EndpointsRegistryUpdater._
//  import registry._
//
//  def receive: Receive = {
//    case Add(endpointDefSet) ⇒
//      update(Added(sender)) { m ⇒
//        val newSet: EndpointDefinitionSets =
//          m.get(endpointDefSet.prefix.value).fold(Set.empty[EndpointDefinitionSet])(_ + endpointDefSet)
//        m + (endpointDefSet.prefix.value → newSet)
//      }
//
//    case Remove(handlerHost) ⇒
//      update(Removed(sender)) { m ⇒
//        m.entries.foldLeft(m) { (m, pair) ⇒
//          val (prefix, endpointDefSets) = pair
//          m + (prefix, endpointDefSets.filter(_.handlerHost == handlerHost))
//        }
//      }
//
//    case Sanitize ⇒ //todo impelement this
//    //      def inCluster(member: Member): Boolean =
//    //        List(MemberStatus.Up, MemberStatus.Joining, MemberStatus.WeaklyUp).contains(member.status)
//    //
//    //      val currentRoles = Cluster(context.system).state.members.filter(inCluster).flatMap(_.roles).toSet
//    //
//    //      log.info("Sanitizing current endpoint defs based current roles " + currentRoles.mkString(", "))
//    //      removeEndpoint(sender) { ef ⇒
//    //        !currentRoles.contains(ef.clusterRole)
//    //      }
//
//    case UpdateSuccess(_, Some(result: Result)) ⇒
//      log.info(s"EndpointRegistry updated by $result")
//      result.confirm()
//  }
//
//  def update(res: Result)(f: LWWMap[EndpointDefinitionSets] ⇒ LWWMap[EndpointDefinitionSets]): Unit = {
//    replicator ! Update(EndpointsDataKey, emptyData, writeConsistency, Some(res))(f)
//  }
//
//}
//
//object EndpointsRegistryUpdater {
//
//  def props(registry: EndpointsRegistry) = Props(new EndpointsRegistryUpdater(registry))
//
//  sealed trait UpdateRequest
//
//  case class Add(endpointDefSet: EndpointDefinitionSet) extends UpdateRequest
//
//  case class AddDoc(role: String, doc: String)
//
//  case class Remove(handlerHost: HandlerHost) extends UpdateRequest
//
//  /**
//   * Remove all endpoints whose role isn't in this list.
//   */
//  case object Sanitize extends UpdateRequest
//
//  sealed trait Result {
//    def replyTo: ActorRef
//    def confirm(): Unit = {
//      replyTo ! this
//    }
//  }
//
//  case class Added(replyTo: ActorRef) extends Result
//  case class Removed(replyTo: ActorRef) extends Result
//  case class Checked(replyTo: ActorRef) extends Result
//
//}
//
