package com.xammel.actors

import akka.actor.typed.{ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object StoppingActors {

  object SensitiveActor {
    def apply(): Behavior[String] = Behaviors
      .receive[String] { (context, message) =>
        context.log.info(s"Received $message")
        if (message == "you're ugly") Behaviors.stopped
        // passing an optional () => Unit  allows you to clear up the resources after actor is stopped
          // OR see below with receiveSignal
        else Behaviors.same
      }
      .receiveSignal { case (context, PostStop) =>
        // Handle the PostStop signal
        // clean up resources that this actor might use eg. DB connections etc.
        context.log.info(s"[${context.self.path.name}] I'm stopping now")
        Behaviors.same // not used anymore in case of stopping
      }
  }

  def main(args: Array[String]): Unit = {

    val userGuardian: Behavior[Unit] = Behaviors.setup { context =>
      val sensitiveActor = context.spawn(SensitiveActor(), "SensitiveActor")

      sensitiveActor ! "Hi"
      sensitiveActor ! "how are you"
      sensitiveActor ! "you're ugly"
      sensitiveActor ! "Hello?"
      sensitiveActor ! "Hi?"

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoStoppingActor")
    Thread.sleep(1000)
    system.terminate()
  }

}
