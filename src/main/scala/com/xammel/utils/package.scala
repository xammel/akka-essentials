package com.xammel

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.xammel.utils.LoggingUtils.logWithName

import scala.concurrent.duration.FiniteDuration

package object utils {
  object LoggerActor {
    def apply[A](): Behavior[A] = Behaviors.receive { (context, message) =>
      logWithName(context)(message)
      Behaviors.same
    }
  }

  implicit class ActorSystemEnhancements[A](system: ActorSystem[A]) {
    def withFiniteLifespan(duration: FiniteDuration): ActorSystem[A] = {
      import system.executionContext
      system.scheduler.scheduleOnce(duration, () => system.terminate())
      system
    }
  }
}
