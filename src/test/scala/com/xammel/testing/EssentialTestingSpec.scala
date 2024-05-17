package com.xammel.testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.xammel.testing.EssentialTestingSpec._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class EssentialTestingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "SimpleActor" should {
    "send back a duplicated message" in {
      val simpleActor = testKit.spawn(SimpleActor(), "simpleActor") // actor under test
      val probe       = testKit.createTestProbe[SimpleProtocol]()   // inspector

      simpleActor ! SimpleMessage("Akka", probe.ref)

      probe.expectMessage(SimpleReply("AkkaAkka"))
    }
  }

  "BlackHoleActore" should {
    "ignore all messages" in {
      val actor = testKit.spawn(BlackHole(), "BlackHole")
      val probe = testKit.createTestProbe[SimpleProtocol]()

      actor ! SimpleMessage("New Message", probe.ref)
      actor ! SimpleMessage("New Message 2", probe.ref)
      actor ! SimpleMessage("New Message 3", probe.ref)

      probe.expectNoMessage(1.second)
    }
  }

  "A simple actor with shared resources" should {
    val simpleActor = testKit.spawn(SimpleActor(), "SimpleActor2")
    val probe       = testKit.createTestProbe[SimpleProtocol]()

    "uppercase a string" in {
      simpleActor ! UpperCaseString("hi there", probe.ref)
      val received = probe.expectMessageType[SimpleReply]
      received.message shouldBe "HI THERE"
    }

    "Send 2 messages for favourite tech" in {
      simpleActor ! FavouriteTech(probe.ref)
      // fetch multiple messages
      val replies: Seq[SimpleProtocol] = probe.receiveMessages(2, 1.second)
      val repliesContents              = replies.collect { case SimpleReply(contents) => contents }

      repliesContents should contain allOf ("Scala", "Akka")
    }
  }
}

object EssentialTestingSpec {
  // code under testing

  sealed trait SimpleProtocol
  case class SimpleMessage(message: String, replyTo: ActorRef[SimpleProtocol])   extends SimpleProtocol
  case class UpperCaseString(message: String, replyTo: ActorRef[SimpleProtocol]) extends SimpleProtocol
  case class FavouriteTech(replyTo: ActorRef[SimpleProtocol])                    extends SimpleProtocol
  case class SimpleReply(message: String)                                        extends SimpleProtocol

  object SimpleActor {
    def apply(): Behavior[SimpleProtocol] = Behaviors.receiveMessage {
      case SimpleMessage(message, replyTo) =>
        replyTo ! SimpleReply(message + message)
        Behaviors.same
      case UpperCaseString(msg, replyRef) =>
        replyRef ! SimpleReply(msg.toUpperCase)
        Behaviors.same
      case FavouriteTech(replyRef) =>
        replyRef ! SimpleReply("Scala")
        replyRef ! SimpleReply("Akka")
        Behaviors.same
    }
  }

  object BlackHole {
    def apply(): Behavior[SimpleProtocol] = Behaviors.ignore
  }
}
