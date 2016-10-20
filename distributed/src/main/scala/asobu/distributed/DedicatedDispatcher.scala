package asobu.distributed

import akka.actor.{Props, ActorSystem}
import akka.dispatch.MessageDispatcher

import scala.concurrent.ExecutionContext

trait DedicatedDispatcher {
  val dispatcherId: String

  implicit class PropsOps(val self: Props) {
    def dedicated = self.withDispatcher(dispatcherId)
  }

  implicit def executionContext(implicit system: ActorSystem): ExecutionContext =
    system.dispatchers.lookup(dispatcherId)
}

object DedicatedDispatcher {

  object service extends DedicatedDispatcher {
    val dispatcherId = "asobu-infra-service" //todo: make this configurable
  }

  object gateway extends DedicatedDispatcher {
    val dispatcherId = "asobu-infra-gateway" //todo: make this configurable
  }

}
