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

  /** Exercises
    *   1. Define two "person" actor behaviors, which receive Strings:
    *      - "happy", which logs your message, e.g. "I've received ____. That's great!"
    *      - "sad", .... "That sucks." Test both.
    *
    * 2. Change the actor behavior:
    *   - the happy behavior will turn to sad() if it receives "Akka is bad."
    *   - the sad behavior will turn to happy() if it receives "Akka is awesome!"
    *
    * 3. Inspect my code and try to make it better.
    */

  object Ex1 {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      if (message.trim == "happy") context.log.info(s"I've received $message. That's great!")
      else if (message.trim == "sad") context.log.info(s"I've received $message. That sucks")
      else context.log.error("Message not recognised")

      Behaviors.same
    }
  }

  def ex1Demo: Unit = {
    val actorSystem = ActorSystem(Ex1(), "Ex1ActorSystem")

    actorSystem ! "happy"
    actorSystem ! "sad"
    actorSystem ! "weird message"

    // 4 - shut down
    Thread.sleep(1000)
    actorSystem.terminate()
  }

  def main(args: Array[String]): Unit = {
//    demoSimpleActor
    ex1Demo
  }

}
