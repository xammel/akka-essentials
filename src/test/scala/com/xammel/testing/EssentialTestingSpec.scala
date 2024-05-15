package com.xammel.testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.xammel.testing.EssentialTestingSpec._
import org.scalatest.wordspec.AnyWordSpecLike

class EssentialTestingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "SimpleActor" should {
    "send back a duplicated message" in {
      val simpleActor = testKit.spawn(SimpleActor(), "simpleActor") // actor under test
      val probe       = testKit.createTestProbe[SimpleProtocol]()   // inspector

      simpleActor ! SimpleMessage("Akka", probe.ref)

      probe.expectMessage(SimpleReply("AkkaAkka"))
    }
  }
}

object EssentialTestingSpec {
  // code under testing

  sealed trait SimpleProtocol
  case class SimpleMessage(message: String, replyTo: ActorRef[SimpleProtocol]) extends SimpleProtocol
  case class SimpleReply(message: String)                                      extends SimpleProtocol

  object SimpleActor {
    def apply(): Behavior[SimpleProtocol] = Behaviors.receiveMessage { case SimpleMessage(message, replyTo) =>
      replyTo ! SimpleReply(message + message)
      Behaviors.same
    }
  }
}
