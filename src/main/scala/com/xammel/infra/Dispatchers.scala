package com.xammel.infra

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector}
import com.typesafe.config.ConfigFactory
import com.xammel.infra.Schedulers.LoggerActor
import com.xammel.utils.ActorSystemEnhancements

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

object Dispatchers {

  // Dispatchers are in charge of handling messages in our actor system

  def demoDispatcherConfig: Unit = {
    val userGuardian = Behaviors.setup[Unit] { ctx =>
      val childActorDispatcherDefault = ctx.spawn(LoggerActor(), "childDefault1", DispatcherSelector.default())
      val childActorBlocking          = ctx.spawn(LoggerActor(), "childDefault2", DispatcherSelector.blocking())
      val childActorInherit           = ctx.spawn(LoggerActor(), "childDefault3", DispatcherSelector.sameAsParent())
      val childActorConfig            = ctx.spawn(LoggerActor(), "childDefault4", DispatcherSelector.fromConfig("my-dispatcher"))

      val actors = (1 to 10).map(i => ctx.spawn(LoggerActor(), s"child$i", DispatcherSelector.fromConfig("my-dispatcher")))

      val r = new Random()
      (1 to 1000).foreach(i => actors(r.nextInt(10)) ! s"task$i")
      Behaviors.empty
    }

    ActorSystem(userGuardian, "demoDispatchers").withFiniteLifespan(2.seconds)
  }

  object DBActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      import context.executionContext
      Future {
        Thread.sleep(1000)
        println(s"Query successful: $message")
      }

      Behaviors.same
    }
  }

  def demoBlockingCalls: Unit = {

    val userGuardian = Behaviors.setup[Unit] { context =>
      val logger  = context.spawn(LoggerActor(), "logger")
      val dbActor = context.spawn(DBActor(), "db", DispatcherSelector.fromConfig("dedicated-blocking-dispatcher"))
      (1 to 100).foreach { i =>
        val message = s"query $i"
        dbActor ! message
        logger ! message

      }
        Behaviors.same
    }

    val system = ActorSystem(userGuardian, "blockingCalls", ConfigFactory.load().getConfig("dispatchers-demo"))
  }
  def main(args: Array[String]): Unit = {
//    demoDispatcherConfig
    demoBlockingCalls
  }
}
