package com.xammel.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

object ActorsIntro {

  // 1 - behaviour
  val simpleActorBehaviour: Behavior[String] = Behaviors.receiveMessage { message: String =>
    /* return new Behaviour[String] that the actor then will use for the next message */
    println(s"[simple-actor] I have received: $message")

    // new behaviour for the next message
    // In this case the behaviour is unchanged.
    Behaviors.same
  }

  def demoSimpleActor: Unit = {
    // 2 - Instantiate the actor
    val actorSystem = ActorSystem(SimpleActor3(), "SimpleActorSystem")

    // 3 - communicate Asynchronously
    // ! = tell method (eg. send a message)
    actorSystem ! "First message!"
    actorSystem ! "Second message!"
    actorSystem ! "Third message!"

    // 4 - shut down
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  // Refactored to be a bit nicer
  // then can call
  //     val actorSystem = ActorSystem(SimpleActor, "SimpleActorSystem")

  object SimpleActor {
    def apply(): Behavior[String] = Behaviors.receiveMessage { message: String =>
      println(s"[simple-actor] I have received: $message")
      Behaviors.same
    }
  }

  object SimpleActor2 {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      // context is a data structure that has access to lots of APIs
      // simple example: logging
      context.log.info(s"[simple-actor] I have received: $message")
      Behaviors.same
    }
  }

  object SimpleActor3 {
    def apply(): Behavior[String] = Behaviors.setup { context =>
      // actor "private" data and methods, behaviours etc

      context.log.info("Starting up SimpleActor3")

      // Behaviour for the FIRST MESSAGE ONLY
      Behaviors.receiveMessage { message: String =>
        context.log.info(s"[simple-actor] I have received: $message")
        Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    demoSimpleActor
  }

}
