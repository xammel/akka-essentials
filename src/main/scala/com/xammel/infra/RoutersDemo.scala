package com.xammel.infra

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.xammel.utils._

import scala.concurrent.duration._

object RoutersDemo {

  def demoPoolRouter: Unit = {
    val workerBehaviour = LoggerActor[String]()
//    val poolRouter      = Routers.pool(5)(workerBehaviour) // Round robin allocation by default
    val poolRouter = Routers.pool(5)(workerBehaviour).withBroadcastPredicate(_.length > 11) // any message with length longer than 11 will be broadcast to all routee actors

    val userGuardian = Behaviors.setup[Unit] { context =>
      val poolActor = context.spawn(poolRouter, "pool")

      (1 to 10).foreach(i => poolActor ! s"work task $i")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "demoSystem").withFiniteLifespan(2.seconds)

  }

  def demoGroupRouter: Unit = {
    val serviceKey = ServiceKey[String]("logWorker")
    // used for discovering actors and fetching their refs

    val userGuardian = Behaviors.setup[Unit] { ctx =>
      val workers = (1 to 5).map(i => ctx.spawn(LoggerActor[String](), s"worker$i"))
      // register workers
      workers.foreach((worker: ActorRef[String]) => ctx.system.receptionist ! Receptionist.Register(serviceKey, worker))

      val groupBehaviour: Behavior[String] = Routers.group(serviceKey).withRoundRobinRouting() // random allocation by default
      val groupRouter                      = ctx.spawn(groupBehaviour, "group")

      (1 to 10).foreach(i => groupRouter ! s"work task $i")

      // add more workers
      Thread.sleep(1000)
      val extraWorker = ctx.spawn(LoggerActor[String](), "extraWorker")
      ctx.system.receptionist ! Receptionist.Register(serviceKey, extraWorker)
      Thread.sleep(1000)

      (1 to 10).foreach(i => groupRouter ! s"work task $i")

      /*
        removing workers:
        - send the receptionist a Receptionist.Deregister(serviceKey, worker, someActorToReceiveConfirmation)
        - receive Receptionist.Deregistered in someActorToReceiveConfirmation, best practice, someActorToReceiveConfirmation == worker
        --- in this time, there's a risk that the router might still use the worker as the routee
        - safe to stop the worker
       */

      Behaviors.empty
    }

    ActorSystem(userGuardian, "demoGroupRouter").withFiniteLifespan(2.seconds)
  }
  def main(args: Array[String]): Unit = {
//    demoPoolRouter
    demoGroupRouter
  }
}
