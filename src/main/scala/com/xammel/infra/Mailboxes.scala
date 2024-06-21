package com.xammel.infra

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, MailboxSelector}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}
import com.xammel.utils._

import scala.concurrent.duration._

object Mailboxes {

  /** Custom priority mailbox : eg. support ticketing system P0, P1, P3 ...
    *
    *   - eg. "[P1] ...."
    *   - eg. "[P0] ...."
    *
    * want to prioritise P0 first
    */

  sealed trait Command
  case class SupportTicket(contents: String) extends Command
  case class Log(contents: String)           extends Command

  class SupportTicketPriorityMailbox(settings: akka.actor.ActorSystem.Settings, config: Config)
      extends UnboundedPriorityMailbox(PriorityGenerator {
        case SupportTicket(contents) if contents.startsWith("[P0]") => 0
        case SupportTicket(contents) if contents.startsWith("[P1]") => 1
        case SupportTicket(contents) if contents.startsWith("[P2]") => 2
        case SupportTicket(contents) if contents.startsWith("[P3]") => 3
        case _                                                      => 4
      })

  def demoSupportTicketMailbox = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val actor = context.spawn(LoggerActor[Command](), "ticketLogger", MailboxSelector.fromConfig("support-ticket-mailbox"))

      actor ! Log("This is received first, but processed last")
      actor ! SupportTicket("[P1] BROKEN")
      actor ! SupportTicket("[P0] BROKEN")
      actor ! SupportTicket("[P3] BROKEN")
      actor ! SupportTicket("[P2] BROKEN")

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoMailbox", ConfigFactory.load().getConfig("mailboxes-demo")).withFiniteLifespan(2.seconds)
  }

  case object ManagementTicket extends ControlMessage with Command

  def demoControlAwareMailbox: Unit = {
    val userGuardian = Behaviors.setup[Unit] { context =>
      val actor = context.spawn(LoggerActor[Command](), "controlAwareLogger", MailboxSelector.fromConfig("control-mailbox"))

      actor ! SupportTicket("[P1] this thing is broken")
      actor ! SupportTicket("[P0] FIX THIS NOW!")
      actor ! ManagementTicket

      Behaviors.empty
    }

    ActorSystem(userGuardian, "DemoControlAwareMailbox", ConfigFactory.load().getConfig("mailboxes-demo")).withFiniteLifespan(2.seconds)
  }

  def main(args: Array[String]): Unit = {
//    demoSupportTicketMailbox
    demoControlAwareMailbox
  }
}
