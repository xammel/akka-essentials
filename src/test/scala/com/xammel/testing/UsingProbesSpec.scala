package com.xammel.testing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike

class UsingProbesSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import UsingProbesSpec._
  "Master" should {
    def createMaster(name: String) = testKit.spawn(Master(), name)
    lazy val workerProbe           = testKit.createTestProbe[WorkerTask]
    lazy val externalProbe         = testKit.createTestProbe[ExternalProtocol]

    "register a worker" in {
      val master = createMaster("1")
      master ! Register(workerProbe.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)
    }

    "send task to worker" in {
      val master = createMaster("2")
      master ! Register(workerProbe.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)

      master ! Work("I love Akka", externalProbe.ref)

      workerProbe.expectMessage(WorkerTask("I love Akka", master.ref, externalProbe.ref))

      master ! WorkCompleted(3, externalProbe.ref) // mocking what the worker actor would be doing

      externalProbe.expectMessage(Report(3))
    }

    "aggregate data correctly" in {
      val master = createMaster("3")

      val mockedWorkerBehaviour = Behaviors.receiveMessage[WorkerTask] { case WorkerTask(_, master, requester) =>
        master ! WorkCompleted(3, requester)
        Behaviors.same
      }
      val workerProbeWithBehaviour = Behaviors.monitor(workerProbe.ref, mockedWorkerBehaviour)
      val mockedWorker             = testKit.spawn(workerProbeWithBehaviour)

      master ! Register(mockedWorker.ref, externalProbe.ref)
      externalProbe.expectMessage(RegisterAck)

      master ! Work("I love Akka", externalProbe.ref)
      master ! Work("I love Akka", externalProbe.ref)

      externalProbe.expectMessage(Report(3))
      externalProbe.expectMessage(Report(6))

    }
  }
}

object UsingProbesSpec {

  /*
  Requester -> master -> worker
  Requester <- master <-
   */

  sealed trait MasterProtocol
  case class Work(text: String, replyTo: ActorRef[ExternalProtocol])                                           extends MasterProtocol
  case class WorkCompleted(count: Int, originalDestination: ActorRef[ExternalProtocol])                        extends MasterProtocol
  case class Register(workerActor: ActorRef[WorkerTask], replyTo: ActorRef[ExternalProtocol])                  extends MasterProtocol
  case class WorkerTask(text: String, master: ActorRef[MasterProtocol], requester: ActorRef[ExternalProtocol]) extends MasterProtocol

  sealed trait ExternalProtocol
  case class Report(totalCount: Int) extends ExternalProtocol
  case object RegisterAck            extends ExternalProtocol

  object Master {
    def apply(): Behavior[MasterProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Register(workerActor, replyTo) =>
          replyTo ! RegisterAck
          online(workerActor, 0)
        case _ =>
          context.log.info("Worker not registered...")
          Behaviors.same
      }
    }

    def online(workerRef: ActorRef[WorkerTask], totalCount: Int): Behavior[MasterProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case Work(text, requester) =>
          workerRef ! WorkerTask(text, context.self, requester)
          Behaviors.same
        case WorkCompleted(count, originalDestination) =>
          val newTotalCount = count + totalCount
          originalDestination ! Report(newTotalCount)
          online(workerRef, newTotalCount)
      }
    }
  }

}
