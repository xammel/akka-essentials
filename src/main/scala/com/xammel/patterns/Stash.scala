package com.xammel.patterns

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.xammel.utils.LoggingUtils.logWithName
import com.xammel.utils.ActorSystemEnhancements
import scala.concurrent.duration._

object Stash {

  // Actor with a locked access to a resource
  sealed trait Command
  case object Open               extends Command
  case object Close              extends Command
  case object Read               extends Command
  case class Write(data: String) extends Command

  /*
  while closed, read and writes can't happen but will stash them for processing when the resource is open
   */
  object ResourceActor {
    def apply(): Behavior[Command] = closed("42")
    def closed(data: String): Behavior[Command] = Behaviors.withStash(128) { buffer =>
      Behaviors.receive { (context, message) =>
        message match {
          case Open =>
            logWithName(context)(s"Opening resource...")
            buffer.unstashAll(open(data)) // open(data) is the next behaviour AFTER unstashing
          case _ =>
            logWithName(context)(s"Resource is currently closed, but will stash $message...")
            buffer.stash(message) // buffer is MUTABLE
            Behaviors.same
        }
      }
    }
    def open(data: String): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case Read =>
          logWithName(context)(s"I have read $data")
          Behaviors.same
        case Write(newData) =>
          logWithName(context)(s"I have written $newData")
          open(newData)
        case Close =>
          logWithName(context)("Closing resource...")
          closed(data)
        case message =>
          logWithName(context)(s"Message $message not supported while file is open")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian: Behavior[Unit] = Behaviors.setup{context =>
      val resourceActor = context.spawn(ResourceActor(), "StashActor")

      resourceActor ! Read //  stashed
      resourceActor ! Open
      resourceActor ! Open
      resourceActor ! Write("new data 43")
      resourceActor ! Close
      resourceActor ! Write("new data 44")
      resourceActor ! Write("new data 45")
      resourceActor ! Read
      resourceActor ! Open

      Behaviors.empty
    }

    ActorSystem(userGuardian, "demo").withFiniteLifespan(5.seconds)
  }

}
