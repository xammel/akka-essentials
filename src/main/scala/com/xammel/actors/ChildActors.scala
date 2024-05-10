package com.xammel.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.xammel.actors.ChildActors.Parent.Command

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

    def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case CreateChild(_) =>
          ctx.log.info(s"Command not supported now")
          Behaviors.same
        case TellChild(message) =>
          ctx.log.info(s"Sending message to child")
          childRef ! message
          Behaviors.same
        case StopChild =>
          ctx.log.info(s"[${ctx.self.path.name}] Stopping the child")
          ctx.stop(childRef) // Will only work for a child actor. won't work for other ActorRefs
          apply()
      }
    }

    def idle: Behavior[Command] = Behaviors.receive { (ctx, msg) =>
      msg match {
        case CreateChild(name) =>
          ctx.log.info(s"Creating child with name $name")
          val childRef: ActorRef[String] = ctx.spawn(Child(), name)
          active(childRef)
        case _ =>
          ctx.log.info("no child exists right now")
          Behaviors.same
      }
    }

    def apply(): Behavior[Command] = idle
  }

  object Child {
    def apply(): Behavior[String] = Behaviors.receive { (ctx, msg) =>
      ctx.log.info(s"Received: $msg")
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

    def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"Creating child of name $name")
          val ref = context.spawn(Sibling(name), name)
          active(children.updated(name, ref))
        case TellChild(nameOfChild, message) =>
          children.get(nameOfChild) match {
            case Some(childRef) =>
              childRef ! message
            case None =>
              context.log.error(s"Can't find child of name $nameOfChild")
          }
          Behaviors.same
        case StopChild(childName) =>
          context.log.info(s"[${context.self.path.name}] Removing $childName")
          active(children.removed(childName))
        case NameChildren =>
          context.log.info(s"My children are: ${children.keys.mkString(", ")}")
          Behaviors.same
      }
    }
    def apply(): Behavior[Command] = active(children = Map.empty)
  }

  object Sibling {

    def active(name: String): Behavior[String] = Behaviors.receive { (ctx, msg) =>
      ctx.log.info(s"[${ctx.self.path}] received message: $msg")
      Behaviors.same
    }

    def apply(name: String): Behavior[String] = active(name)
  }

  def demo1 = {
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      import Parent._

      val parent = context.spawn(Parent(), "Parent")

      parent ! TellChild("hi there kid")
      parent ! CreateChild("kid1")
      parent ! TellChild("hi there kid")
      parent ! TellChild("hi there kid again")
      parent ! StopChild
      parent ! TellChild("hi there kid")

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
//    demo1
    demo2
  }
}
