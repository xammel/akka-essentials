package com.xammel.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.util.Random

object ChildActorsExercise {

  /** Exercise: distributed word counting
    *
    * requester ----- (computational task) ----> WCM ------ (computational task) ----> one child of type WCW
    *
    * requester <---- (computational res) <---- WCM ------ (computational res) <----
    *
    * Scheme for scheduling tasks to children: round robin [1-10] task 1 - child 1 task 2 - child 2 . . . task 10 - child 10 task 11 - child 1 task 12 - child 2 . .
    */

  sealed trait SchedulerProtocol
  case class Initialize(nChildren: Int)                                   extends SchedulerProtocol
  case class WordCountTask(text: String, replyTo: ActorRef[UserProtocol]) extends SchedulerProtocol
  case class WordCountReply(count: Int)                                   extends SchedulerProtocol

  sealed trait WorkerProtocol
  case class WorkerTask(text: String) extends WorkerProtocol

  sealed trait UserProtocol
  case class Reply(count: Int) extends UserProtocol

  object WordCounterScheduler {

    def active(currentChildren: List[ActorRef[WorkerProtocol]], aggregatorRef: Option[ActorRef[UserProtocol]] = None): Behavior[SchedulerProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case WordCountTask(text, replyTo) =>
          context.log.info(s"[${context.self.path.name}] I've received a task, sending it to ${currentChildren.head.path.name}")
          currentChildren.head ! WorkerTask(text)
          active(currentChildren.tail :+ currentChildren.head, Some(replyTo))
        case WordCountReply(count) =>
          aggregatorRef.get ! Reply(count)
          Behaviors.same
        case Initialize(_) =>
          context.log.info(s"Already initialized...")
          Behaviors.same
      }
    }

    def apply(): Behavior[SchedulerProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Initialize(nChildren) =>
          context.log.info(s"[${context.self.path.name}] Initializing with $nChildren children")
          val children: List[ActorRef[WorkerProtocol]] = (1 to nChildren).toList.map(id => context.spawn(WordCounterWorker(context.self), s"WordCounterWorker$id"))
          active(children)
        case _ =>
          context.log.info(s"[${context.self.path.name}] Not ready for tasks yet... need to inialize children")
          Behaviors.same
      }
    }
  }

  object WordCounterWorker {
    def apply(schedulerRef: ActorRef[SchedulerProtocol]): Behavior[WorkerProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case WorkerTask(text) =>
          context.log.info(s"[${context.self.path.name}] computing count of words: $text")
          val count = text.split(" ").map(_.trim).count(_.nonEmpty)
          schedulerRef ! WordCountReply(count)
          Behaviors.same
      }
    }
  }

  object Aggregator {
    def apply(): Behavior[UserProtocol] = active(0)

    def active(totalWords: Int): Behavior[UserProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Reply(count) =>
          context.log.info(s"[${context.self.path.name}] I've receive a count of $count. Total: $totalWords")
          active(totalWords + count)
      }
    }
  }

  def testWordCounter = {
    val userGuardian: Behavior[Unit] = Behaviors.setup { context =>
      val aggregator = context.spawn(Aggregator(), "Aggregator")
      val scheduler  = context.spawn(WordCounterScheduler(), "Scheduler")

      def randomString = (1 to Random.nextInt(20)).map(_ => Random.nextString(2)).mkString(" ")
      scheduler ! Initialize(10)
      (0 to 100).foreach(_ => scheduler ! WordCountTask(randomString, aggregator))

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "WordCounting")
    Thread.sleep(1000)
    system.terminate()
  }
  def main(args: Array[String]): Unit = {
    testWordCounter
  }

}
