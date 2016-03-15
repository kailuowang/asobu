package asobu.distributed.util

import akka.actor.ActorSystem
import akka.testkit.{TestKit, ImplicitSender, TestProbe}
import org.specs2.mutable.Specification
import org.specs2.specification.{Scope, AfterAll}

trait SpecWithActorSystem extends Specification with AfterAll {
  sequential
  implicit lazy val system = ActorSystem()

  def afterAll(): Unit = system.terminate()
}

class ScopeWithActor(implicit system: ActorSystem) extends TestKit(system) with ImplicitSender with Scope {

}
