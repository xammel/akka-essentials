package com.xammel.infra

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.xammel.utils.LoggingUtils._
import com.xammel.utils._

import scala.concurrent.duration._

object Schedulers {

  object LoggerActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      logWithName(context)(message)
      Behaviors.same
    }
  }

  def demoScheduler = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val loggerActor = context.spawn(LoggerActor(), "loggerActor")

      logWithName(context)("starting...")
      context.scheduleOnce(1.second, loggerActor, "reminder")

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoScheduler")

    system.withFiniteLifespan(2.seconds)

  }

  def demoActorWithTimeout: Unit = {
    val timeoutActor: Behavior[String] = Behaviors.receive { (context, message) =>
      val schedule = context.scheduleOnce(1.second, context.self, "timeout")

      // can cancel the schedule with schedule.cancel()

      message match {
        case "timeout" =>
          logWithName(context)("Stopping!")
          Behaviors.stopped
        case _ =>
          logWithName(context)(s"Received $message")
          Behaviors.same
      }
    }

    val system = ActorSystem(timeoutActor, "TimeoutActor")

    system ! "trigger"
    Thread.sleep(3000)
    system ! "hello?"
  }

  object ResettingActor {

    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      logWithName(context)(s"Received $message")
      resettingActor(context.scheduleOnce(1.second, context.self, "timeout"))
    }

    def resettingActor(schedule: Cancellable): Behavior[String] = Behaviors.receive { (context, message) =>
      message match {
        case "timeout" =>
          logWithName(context)("Stopping!")
          Behaviors.stopped
        case _ =>
          schedule.cancel
          logWithName(context)(s"Received $message")
          resettingActor(context.scheduleOnce(1.second, context.self, "timeout"))
      }
    }
  }

  def demoResetting = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val resettingActor = context.spawn(ResettingActor(), "ResettingActor")

      resettingActor ! "start timer"

      Thread.sleep(500)

      resettingActor ! "reset"

      Thread.sleep(700)

      resettingActor ! "reset again"

      Thread.sleep(700)

      resettingActor ! "reset again"

      Thread.sleep(700)

      resettingActor ! "reset again"

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "user")

    system.withFiniteLifespan(3.seconds)
  }

  def main(args: Array[String]): Unit = {
//    demoScheduler
//    demoActorWithTimeout
    demoResetting
  }
}
