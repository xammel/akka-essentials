package com.xammel.testing

import akka.actor.testkit.typed.CapturedLogEvent
import akka.actor.testkit.typed.Effect.Spawned
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import com.xammel.actors.ChildActorsExercise._
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.event.Level

class SynchronousTestingSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A word counter master" should {
    "spawn children when it receives the initialize message" in {
      lazy val master = BehaviorTestKit(WordCounterScheduler())
      master.run(Initialize(1)) // synchronous "sending" of the message Initialize(1)
      lazy val effect: Spawned[WorkerProtocol] = master.expectEffectType[Spawned[WorkerProtocol]]

      // inspect contents of the effects
      effect.childName shouldBe "WordCounterWorker1"
    }

    "send a task to a child" in {
      lazy val master = BehaviorTestKit(WordCounterScheduler())
      master.run(Initialize(1)) // synchronous "sending" of the message Initialize(1)

      val mailbox = TestInbox[UserProtocol]() // the requesters inbox
      master.run(WordCountTask("wow this framework is cool", mailbox.ref))

      // mock the reply from the child
      master.run(WordCountReply(5))

      mailbox.expectMessage(Reply(5))
    }

    "log messages" in {
      lazy val master = BehaviorTestKit(WordCounterScheduler())
      master.run(Initialize(1)) // synchronous "sending" of the message Initialize(1)

      master.logEntries() shouldBe Seq(CapturedLogEvent(Level.INFO, "[testkit] Initializing with 1 children"))
    }
  }
}

object SynchronousTestingSpec {}
