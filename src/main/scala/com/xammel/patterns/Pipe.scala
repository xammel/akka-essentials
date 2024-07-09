package com.xammel.patterns

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.xammel.utils.ActorSystemEnhancements
import com.xammel.utils.LoggingUtils.logWithName

import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object Pipe {

  // interaction with external service that returns Futures
  val db: Map[String, Int] = Map(
    "Dan"  -> 123,
    "Max"  -> 456,
    "Jane" -> 789
  )

  val executor                      = Executors.newFixedThreadPool(4)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executor) // for running external service

  def fetchNumberFromDB(name: String): Future[Int] = {
    // SELECT phoneNo FROM ....
    Future(db(name))
  }

  sealed trait PhoneCallProtocol
  case class FindAndCallPhoneNumber(name: String)   extends PhoneCallProtocol
  case class InitiatePhoneCall(number: Int)         extends PhoneCallProtocol
  case class LogPhoneCallFailure(reason: Throwable) extends PhoneCallProtocol

  object PhoneCallActor {
    def apply(): Behavior[PhoneCallProtocol] = Behaviors.receive { (ctx, message) =>
      message match {
        case FindAndCallPhoneNumber(name) =>
          logWithName(ctx)(s"Fetching number for $name")
          val phoneNumber: Future[Int] = fetchNumberFromDB(name)
          // pipe the future result back to me as a message
          ctx.pipeToSelf(phoneNumber) {
            case Success(value)     => InitiatePhoneCall(value)
            case Failure(exception) => LogPhoneCallFailure(exception)
          }
          Behaviors.same
        case InitiatePhoneCall(number) =>
          logWithName(ctx)(s"Initiating phone call to $number")
          Behaviors.same
        case LogPhoneCallFailure(reason) =>
          logWithName(ctx)(s"Initiating phone call failed: $reason")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val phoneCallActor = context.spawn(PhoneCallActor(), "phoneCallActor")

      phoneCallActor ! FindAndCallPhoneNumber("Dan")
      phoneCallActor ! FindAndCallPhoneNumber("Fake person")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoPipePattern").withFiniteLifespan(5.seconds)
  }
}
