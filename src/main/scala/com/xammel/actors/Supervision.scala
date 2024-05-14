package com.xammel.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy, Terminated}
import com.xammel.utils.LoggingUtils.logWithName

import scala.concurrent.duration._
import scala.reflect.runtime.universe

object Supervision {

  object FussyWordCounter {

    def apply(): Behavior[String] = active(0)

    def active(total: Int): Behavior[String] = Behaviors.receive { (context, message) =>
      val wordCount = message.split(" ").length
      logWithName(context)(s"Received text: '$message', counted $wordCount words, total: ${total + wordCount}")

      if (message.startsWith("Q")) throw new RuntimeException("I hate queues...")
      else active(total + wordCount)
    }
  }

  // If an actor throws an exception, it gets killed

  def demoCrash = {
    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(FussyWordCounter(), "WordCounter")

      fussyCounter ! "Message one"
      fussyCounter ! "Message two"
      fussyCounter ! "Quick! Hi"
      // following messages will be delivered to deadletters, not the actor (which is dead)
      fussyCounter ! "Message 3"
      fussyCounter ! "Message 4"

      Behaviors.empty
    }

    val actorSystem = ActorSystem(guardian, "demoCrash")
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  def demoWithParent = {
    val parent = Behaviors.setup[String] { context =>
      val child = context.spawn(FussyWordCounter(), "fussyChild")

      context.watch(child)

      Behaviors
        .receiveMessage[String] { message =>
          child ! message
          Behaviors.same
        }
        .receiveSignal { case (context, Terminated(childRef)) =>
          logWithName(context)(s"Child ${childRef.path.name} failed")
          Behaviors.same
        }
    }

    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(parent, "ParentWithChildCounter")

      fussyCounter ! "Message one"
      fussyCounter ! "Message two"
      fussyCounter ! "Quick! Hi"
      // following messages will be delivered to deadletters, not the actor (which is dead)
      fussyCounter ! "Message 3"
      fussyCounter ! "Message 4"

      Behaviors.empty
    }

    val actorSystem = ActorSystem(guardian, "demoCrashWithParent")
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  def demoWithRestart = {
    val parent = Behaviors.setup[String] { context =>
      // supervise the child with a restart strategy
      val childBehaviour = Behaviors.supervise(FussyWordCounter()).onFailure[RuntimeException](SupervisorStrategy.resume)
      val child          = context.spawn(childBehaviour, "fussyChild")

      context.watch(child)

      Behaviors
        .receiveMessage[String] { message =>
          child ! message
          Behaviors.same
        }
        .receiveSignal { case (context, Terminated(childRef)) =>
          logWithName(context)(s"Child ${childRef.path.name} failed")
          Behaviors.same
        }
    }

    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(parent, "ParentWithChildCounter")

      fussyCounter ! "Message one"
      fussyCounter ! "Message two"
      fussyCounter ! "Quick! Hi" // kills child
      fussyCounter ! "Still there are you?"
      fussyCounter ! "Still there are you?"
      fussyCounter ! "Still there are you?"

      Behaviors.empty
    }

    val actorSystem = ActorSystem(guardian, "demoCrashWithParent")
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  def demoWithDifferentSupervisionStrategies = {
    val parent = Behaviors.setup[String] { context =>
      // can implement different strategies for different errors like below
      // The cases are tested inside out
      // eg. in this example, the exception will be testing against the RuntimeException before the NullPointerException
      // Therefore, put the most SPECIFIC exceptions on the inside, and the most general on the outside.
      val childBehaviour = Behaviors
        .supervise(
          Behaviors
            .supervise(FussyWordCounter())
            .onFailure[RuntimeException](SupervisorStrategy.restartWithBackoff(1.second, 1.minute, 0.2))
        )
        .onFailure[NullPointerException](SupervisorStrategy.resume)

      val child = context.spawn(childBehaviour, "fussyChild")

      context.watch(child)

      Behaviors
        .receiveMessage[String] { message =>
          child ! message
          Behaviors.same
        }
        .receiveSignal { case (context, Terminated(childRef)) =>
          logWithName(context)(s"Child ${childRef.path.name} failed")
          Behaviors.same
        }
    }

    val guardian: Behavior[Unit] = Behaviors.setup { context =>
      val fussyCounter = context.spawn(parent, "ParentWithChildCounter")

      fussyCounter ! "Message one"
      fussyCounter ! "Message two"
      fussyCounter ! "Quick! Hi" // kills child
      fussyCounter ! "Still there are you?"

      Behaviors.empty
    }

    val actorSystem = ActorSystem(guardian, "demoCrashWithParent")
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  /** Exercise: how do we specify different supervisor strategies for different exception types?
    */

  def main(args: Array[String]): Unit = {
//    demoCrash
//    demoWithParent
    demoWithRestart
  }

}
