package com.xammel.patterns

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.util.Timeout
import com.xammel.utils.ActorSystemEnhancements
import com.xammel.utils.LoggingUtils.logWithName

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object Ask {

  sealed trait WorkProtocol
  case class ComputationalTask(payload: String, replyTo: ActorRef[WorkProtocol]) extends WorkProtocol
  case class ComputationResult(result: Int)                                      extends WorkProtocol

  object Worker {
    def apply(): Behavior[WorkProtocol] = Behaviors.receive { (ctx, message) =>
      message match {
        case ComputationalTask(payload, replyTo) =>
          logWithName(ctx)(s"crunching data for $payload")
          replyTo ! ComputationResult(payload.split(" ").length)
          Behaviors.same
        case ComputationResult(result) =>
          Behaviors.same
      }
    }
  }

  def askSimple: Unit = {
    import akka.actor.typed.scaladsl.AskPattern._

    val system = ActorSystem(Worker(), "DemoAskSimple").withFiniteLifespan(5.seconds)

    implicit val timeout: Timeout     = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    val reply: Future[WorkProtocol] = system.ask(ref => ComputationalTask("test sentence", ref))
    //                                            ^ temporary actor  ^ message that gets sent to the worker == the user guardian
    implicit val ec: ExecutionContextExecutor = system.executionContext
    reply.foreach(println)

  }

  def askFromWithinAnotherActor = {
    val userGuardian = Behaviors.setup[WorkProtocol] { context =>
      val worker = context.spawn(Worker(), "Worker")

      case class ExtendedResult(count: Int, description: String) extends WorkProtocol

      implicit val timeout: Timeout = Timeout(3.seconds)

      context.ask(worker, ref => ComputationalTask("this ask pattern is more complicated", ref)) {
        // Try[WorkerProtocol] => WorkProtocol message that will be sent to ME later

        case Success(ComputationResult(result)) => ExtendedResult(result, "wow this is a description")
        case Failure(exception)                 => ExtendedResult(-1, s"Computation failed... $exception")
      }

      Behaviors.receiveMessage {
        case ExtendedResult(count, description) =>
          logWithName(context)(s"Ask and ye shall receive: $description - $count")
          Behaviors.same
        case _ => Behaviors.same
      }

    }

    val system = ActorSystem(userGuardian, "demoAskConvoluted").withFiniteLifespan(5.seconds)
  }

  def main(args: Array[String]): Unit = {
//    askSimple
    askFromWithinAnotherActor
  }

}
