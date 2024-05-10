package com.xammel.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}

object ActorState {

  /*
  Exercise: use the setup method to create a word counter which upon receiving a message will
  - split it into two words
  - keep track of total number of words
  - log current  number of words and total
   */

  object WordCounter {

    def apply(): Behavior[String] = Behaviors.setup { ctx =>
      var total = 0

      Behaviors.receiveMessage { message =>
        val split    = message.split(" ").filter(_.nonEmpty)
        val newCount = split.length
        total += newCount
        ctx.log.info(s"Message word count: $newCount, total: $total")
        Behaviors.same
      }

    }
  }

  sealed trait SimpleThing
  case object EatChoclate     extends SimpleThing
  case object CleanUpTheFloor extends SimpleThing
  case object LearnAkka       extends SimpleThing

  /*
  Message types must be IMMUTABLE and SERIALIZABLE
  very important for Akka
  Cannot be checked by compiler - need to check yourself

  Good to do:
  - use case classes/objects - these are serializable
  - use a FLAT type hierarchy
   */

  object SimpleHuman {
    def apply(): Behavior[SimpleThing] = Behaviors.setup { ctx =>
      var happiness = 0
      Behaviors.receiveMessage {
        case CleanUpTheFloor =>
          happiness -= 1
          ctx.log.info(s"cleaning floor :( $happiness")
          Behaviors.same
        case EatChoclate =>
          happiness += 1
          ctx.log.info(s"eating choc :)  $happiness")
          Behaviors.same
        case LearnAkka =>
          happiness += 1
          ctx.log.info(s"learning akka :) $happiness")
          Behaviors.same
      }
    }
  }

  def simpleHumanDemo = {
    val simpleHuman = ActorSystem(SimpleHuman(), "SimpleHumanActor")

    simpleHuman ! LearnAkka
    simpleHuman ! LearnAkka
    simpleHuman ! LearnAkka
    simpleHuman ! EatChoclate
    simpleHuman ! CleanUpTheFloor
    simpleHuman ! CleanUpTheFloor

    Thread.sleep(1000)
    simpleHuman.terminate()
  }

  object SimpleHumanStateless {

    def statelessHuman(happiness: Int): Behavior[SimpleThing] = Behaviors.receive { (ctx, message) =>
      message match {
        case EatChoclate =>
          ctx.log.info(s"eating choc :)  $happiness")
          statelessHuman(happiness + 1)
        case CleanUpTheFloor =>
          ctx.log.info(s"cleaning floor :( $happiness")
          statelessHuman(happiness - 1)
        case LearnAkka =>
          ctx.log.info(s"learning akka :) $happiness")
          statelessHuman(happiness - 1)
      }
    }
    def apply(): Behavior[SimpleThing] = statelessHuman(0)
  }

  /*
  Tips to refactor a stateful actor with variables to a stateless actor with immutable values:
  - each var/mutable field becomes an immutable METHOD ARGUMENT
  - each state change = new behaviour obtained by calling the method with a different argument
   */

  def statelessHumanDemo = {
    val statelessHuman = ActorSystem(SimpleHumanStateless(), "StatelessHuman")

    statelessHuman ! EatChoclate
    statelessHuman ! EatChoclate
    statelessHuman ! EatChoclate
    statelessHuman ! LearnAkka
    statelessHuman ! CleanUpTheFloor
    statelessHuman ! CleanUpTheFloor
    statelessHuman ! CleanUpTheFloor

    Thread.sleep(1000)
    statelessHuman.terminate()
  }

  /** Exercise: refactor the "stateful" word counter into a "stateless" version.
    */

  object WordCounterStateless {

    def increment(total: Int): Behavior[String] = Behaviors.receive { (ctx, message) =>
      val split = message.split(" ").filter(_.nonEmpty)
      val newCount = split.length
      val newTotal = total + newCount
      ctx.log.info(s"Message word count: $newCount, total: $newTotal")
      increment(newTotal)
    }

    def apply(): Behavior[String] = increment(0)
  }

  def demoWordCount = {
    val counter = ActorSystem(WordCounterStateless(), "WordCounter")

    counter ! "hi there how are you"
    counter ! "not bad thanks"
    counter ! "weathers a bit shit hey"

    Thread.sleep(1000)
    counter.terminate()
  }

  def main(args: Array[String]): Unit = {
//    simpleHumanDemo
//    statelessHumanDemo
    demoWordCount
  }

}
