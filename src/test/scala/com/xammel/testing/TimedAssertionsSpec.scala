package com.xammel.testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.util.Random

class TimedAssertionsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import TimedAssertionsSpec._

  "WorkerActor" should {
    val workerActor = testKit.spawn(WorkerActor())
    val probe       = testKit.createTestProbe[ResultMessage]

    "reply with a result within a second" in {
      workerActor ! Work(probe.ref)

      probe.expectMessage(1.second, WorkResult(42))
    }

    "reply with a result in at least half a second" in {
      workerActor ! Work(probe.ref)
      probe.expectNoMessage(500.millis)
      probe.expectMessage(500.millis, WorkResult(42))
    }

    "reply with a result in between 0.5 and 1 second" in {
      workerActor ! Work(probe.ref)

      probe.within(500.millis, 1.second) {
        // scenario, run as many assertions as you like
        probe.expectMessage(WorkResult(42))
      }
    }

    "reply with multiple results in a timely manner" in {
      workerActor ! WorkSequence(probe.ref)

      val results = probe.receiveMessages(10, 1.second)

      val resultsSeq = results.collect {case WorkResult(v) => v}

      resultsSeq.sum should be > 5

    }
  }
}

object TimedAssertionsSpec {
  sealed trait Message
  case class Work(replyTo: ActorRef[ResultMessage])         extends Message
  case class WorkSequence(replyTo: ActorRef[ResultMessage]) extends Message

  sealed trait ResultMessage
  case class WorkResult(result: Int) extends ResultMessage

  object WorkerActor {
    def apply(): Behavior[Message] = Behaviors.receiveMessage {
      case Work(replyRef) =>
        // wait a bit to simulate a long computation
        Thread.sleep(500)
        replyRef ! WorkResult(42)
        Behaviors.same
      case WorkSequence(replyRef) =>
        val random = new Random()
        (1 to 10).foreach { _ =>
          Thread.sleep(random.nextInt(50))
          replyRef ! WorkResult(1)
        }
        Behaviors.same
    }

  }
}
