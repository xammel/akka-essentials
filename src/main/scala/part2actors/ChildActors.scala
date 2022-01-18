package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

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
    trait Command
    case class CreateChild(name: String) extends Command
    case class TellChild(message: String) extends Command

    def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[parent] Creating child with name $name")
          // creating a child actor REFERENCE (used to send messages to this child)
          val childRef: ActorRef[String] = context.spawn(Child(), name)
          active(childRef)
      }
    }

    def active(childRef: ActorRef[String]): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case TellChild(message) =>
          context.log.info(s"[parent] Sending message $message to child")
          childRef ! message // <- send a message to another actor
          Behaviors.same
        case _ =>
          context.log.info("[parent] command not supported")
          Behaviors.same
      }
    }
  }

  object Child {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(s"[${context.self.path.name}] Received $message")
      Behaviors.same
    }
  }

  def demoParentChild(): Unit = {
    import Parent._
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      // set up all the important actors in your application
      val parent = context.spawn(Parent(), "parent")
      // set up the initial interaction between the actors
      parent ! CreateChild("child")
      parent ! TellChild("hey kid, you there?")

      // user guardian usually has no behavior of its own
      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChild")
    Thread.sleep(1000)
    system.terminate()
  }

  /**
   * Exercise: write a Parent_V2 that can manage MULTIPLE child actors.
   */
  object Parent_V2 {
    trait Command
    case class CreateChild(name: String) extends Command
    case class TellChild(name: String, message: String) extends Command

    def apply(): Behavior[Command] = active(Map())

    def active(children: Map[String, ActorRef[String]]): Behavior[Command] = Behaviors.receive { (context, message) =>
      message match {
        case CreateChild(name) =>
          context.log.info(s"[parent] Creating child '$name'")
          val childRef = context.spawn(Child(), name)
          active(children + (name -> childRef))
        case TellChild(name, message) =>
          val childOption = children.get(name)
          childOption.fold(context.log.info(s"[parent] Child '$name' could not be found"))(child => child ! message)
          Behaviors.same
      }
    }
  }

  def demoParentChild_v2(): Unit = {
    import Parent_V2._
    val userGuardianBehavior: Behavior[Unit] = Behaviors.setup { context =>
      val parent = context.spawn(Parent_V2(), "parent")
      parent ! CreateChild("alice")
      parent ! CreateChild("bob")
      parent ! TellChild("alice", "living next door to you")
      parent ! TellChild("daniel", "I hope your Akka skills are good")

      Behaviors.empty
    }

    val system = ActorSystem(userGuardianBehavior, "DemoParentChildV2")
    Thread.sleep(1000)
    system.terminate()
  }

  def main(args: Array[String]): Unit = {
    demoParentChild_v2()
  }
}