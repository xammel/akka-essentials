package com.xammel.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import com.xammel.utils.LoggingUtils.{Error, logWithName}

object ChildActors {

  /*
  - actors can create other actors (children): parent -> child -> grandChild -> ...
                                                      -> child2 -> ...
  - actor hierarchy = tree-like structure
  - root of the hierarchy = "guardian" actor (created with the ActorSystem)
  - actors can be identified via a path: /user/parent/child/grandChild/
  - ActorSystem creates
    - the top-level (root) guardian, with children
      - system guardian (for Akka internal messages)
      - user guardian (for our custom actors)
  - ALL OUR ACTORS are child actors of the user guardian
   */

  object Parent {
    sealed trait Command
    case class CreateChild(name: String)  extends Command
    case class TellChild(message: String) extends Command
    case object StopChild                 extends Command
    case object WatchChild                extends Command

    def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors
      .receive[Command] { (ctx, msg) =>

        msg match {
          case CreateChild(_) =>
            logWithName(ctx)(s"Command not supported now")
            Behaviors.same
          case TellChild(message) =>
            logWithName(ctx)(s"Sending message to child ${childRef.path.name}")
            childRef ! message
            Behaviors.same
          case StopChild =>
            logWithName(ctx)(s"Stopping the child ${childRef.path.name}")
            ctx.stop(childRef) // Will only work for a child actor. won't work for other ActorRefs
            idle
          case WatchChild =>
            logWithName(ctx)(s"Watching child ${childRef.path.name}")
            ctx.watch(childRef) // can use for ANY actorRef
            Behaviors.same
        }
      }
      .receiveSignal { case (context, Terminated(deadChildRef)) => handleTermination(context, deadChildRef) }

    def idle: Behavior[Command] = Behaviors
      .receive[Command] { (ctx, msg) =>
        msg match {
          case CreateChild(name) =>
            logWithName(ctx)(s"Creating child with name $name")
            val childRef: ActorRef[String] = ctx.spawn(Child(), name)
            active(childRef)
          case _ =>
            logWithName(ctx)("no child exists right now")
            Behaviors.same
        }
      }
      .receiveSignal { case (context, Terminated(deadChildRef)) => handleTermination(context, deadChildRef) }

    def handleTermination(context: ActorContext[_], deadChildRef: ActorRef[_]) = {
      logWithName(context)(s"Child ${deadChildRef.path} was killed by something...")
      idle
    }

    def apply(): Behavior[Command] = idle
  }

  object Child {
    def apply(): Behavior[String] = Behaviors.receive { (ctx, msg) =>
      logWithName(ctx)(s"Received: $msg")
      Behaviors.same
    }
  }

  /** Exercise: write a Parent_V2 that can manage MULTIPLE child actors.
    */

  object MultiChildParent {
    sealed trait Command
    case class CreateChild(name: String)                       extends Command
    case class TellChild(nameOfChild: String, message: String) extends Command
    case class StopChild(name: String)                         extends Command
    case object NameChildren                                   extends Command
    case class WatchChild(nameOfChild: String)                 extends Command

    def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case CreateChild(name) =>
            logWithName(context)(s"Creating child of name $name")
            val ref = context.spawn(Sibling(name), name)
            active(children.updated(name, ref))
          case TellChild(nameOfChild, message) =>
            children.get(nameOfChild) match {
              case Some(childRef) =>
                childRef ! message
              case None =>
                logWithName(context)(s"Can't find child of name $nameOfChild", logLevel = Error)
            }
            Behaviors.same
          case StopChild(childName) =>
            logWithName(context)(s"Removing $childName")
            children.get(childName) match {
              case Some(childRef) => context.stop(childRef)
              case None           => logWithName(context)(s"Cannot stop $childName, does not exist", Error)
            }
            active(children.removed(childName))
          case NameChildren =>
            logWithName(context)(s"My children are: ${children.keys.mkString(", ")}")
            Behaviors.same
          case WatchChild(childName) =>
            children.get(childName) match {
              case Some(childRef) =>
                logWithName(context)(s"Watching child $childName")
                context.watch(childRef)
              case None => logWithName(context)(s"Cannot find child of name $childName. Not being watched.", logLevel = Error)
            }
            Behaviors.same
        }
      }
      .receiveSignal { case (context, Terminated(deadChildRef)) =>
        logWithName(context)(s"${deadChildRef.path} killed by something")
        active(children - deadChildRef.path.name)
      }

    def apply(): Behavior[Command] = active(children = Map.empty)
  }

  object Sibling {

    def active(name: String): Behavior[String] = Behaviors.receive { (ctx, msg) =>
      logWithName(ctx)(s"Received message: $msg")
      Behaviors.same
    }

    def apply(name: String): Behavior[String] = active(name)
  }

  def demo1 = {
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      import Parent._

      val parent = context.spawn(Parent(), "Parent")

      parent ! CreateChild("kid1")
      parent ! TellChild("hi there kid")
      parent ! TellChild("hi there kid again")
      parent ! WatchChild
      parent ! StopChild
      parent ! CreateChild("kid2")
      parent ! TellChild("hi there kid")

//      parent ! CreateChild("child")
//      parent ! TellChild("hey kid, you there?")
//      parent ! WatchChild
//      parent ! StopChild
//      parent ! CreateChild("child2")
//      parent ! TellChild("yo new kid, how are you?")

      // User guardian usually has no behaviour of its own
      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChild")
    Thread.sleep(1000)
    system.terminate()
  }
  def demo2 = {
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      import MultiChildParent._

      val parent = context.spawn(MultiChildParent(), "MultiChildrenParent")

      parent ! TellChild("kid1", "hi there kid")
      parent ! CreateChild("kid1")
      parent ! CreateChild("kid2")
      parent ! CreateChild("kid3")
      parent ! TellChild("kid1", "hi there kid")
      parent ! TellChild("kid2", "hi there kid again")
      parent ! TellChild("kid3", "hi there kid again again")
      parent ! TellChild("fakeKid", "hi there kid again again")
      parent ! NameChildren
      parent ! WatchChild("kid1")
      parent ! StopChild("kid1")
      parent ! NameChildren

      // User guardian usually has no behaviour of its own
      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChild")
    Thread.sleep(1000)
    system.terminate()
  }
  def main(args: Array[String]): Unit = {
    demo1
//    demo2
  }
}
