package com.xammel.testing

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.xammel.utils.LoggingUtils.logWithName
import org.scalatest.wordspec.AnyWordSpecLike

class InterceptingLogsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import InterceptingLogsSpec._

  lazy val item              = "bag of crisps"
  lazy val validCreditCard   = "1234-1234-1234-1234"
  lazy val invalidCreditCard = "0234-1234-1234-1234"
  lazy val checkoutActor     = testKit.spawn(CheckoutActor(), "CheckoutActor")

  "CheckoutActor" should {
    "correctly dispatch an order for a valid credit card" in {
      LoggingTestKit
        .info("Order")
        .withMessageRegex(s"Order [0-9]+ for item $item has been dispatched.")
        .withOccurrences(1)
        .expect { // scenario under test
          checkoutActor ! Checkout(item, validCreditCard)
        }
    }

    "freak out if credit card is declined" in {
      LoggingTestKit
        .error[RuntimeException]
        .withOccurrences(1)
        .expect {
          checkoutActor ! Checkout(item, invalidCreditCard)
        }
    }
  }
}

object InterceptingLogsSpec {
  // Test payment system
  // 3 actors: checkout,
  /*
   checkout -> payment manager
            <-

            -> fulfilment manager
            <-

   */

  sealed trait PaymentProtocol
  case class Checkout(item: String, creditCardDetails: String)                            extends PaymentProtocol
  case class AuthorizeCard(creditCardDetails: String, replyTo: ActorRef[PaymentProtocol]) extends PaymentProtocol
  case object PaymentAccepted                                                             extends PaymentProtocol
  case object PaymentDenied                                                               extends PaymentProtocol
  case class DispatchOrder(item: String, replyTo: ActorRef[PaymentProtocol])              extends PaymentProtocol
  case object OrderConfirmed                                                              extends PaymentProtocol

  object CheckoutActor {
    def apply(): Behavior[PaymentProtocol] = Behaviors.setup { context =>
      val paymentManagement = context.spawn(PaymentManagementActor(), "PaymentManager")
      val fulfilmentManager = context.spawn(FulfilmentManagerActor(), "FulfilmentManager")

      def awaitingCheckout: Behavior[PaymentProtocol] = Behaviors.receiveMessage {
        case Checkout(item, creditCardDetails) =>
          logWithName(context)(s"Received order for item $item")
          paymentManagement ! AuthorizeCard(creditCardDetails, context.self)
          pendingPayment(item)

        case _ => Behaviors.same
      }

      def pendingPayment(item: String): Behavior[PaymentProtocol] = Behaviors.receiveMessage {
        case PaymentAccepted =>
          fulfilmentManager ! DispatchOrder(item, context.self)
          pendingDispatch(item)
        case PaymentDenied => throw new RuntimeException("Payment declined")
      }

      def pendingDispatch(item: String): Behavior[PaymentProtocol] = Behaviors.receiveMessage { case OrderConfirmed =>
        logWithName(context)("Dispatch confirmed")
        awaitingCheckout
      }

      // Initial state
      awaitingCheckout
    }
  }
  object PaymentManagementActor {
    def apply(): Behavior[PaymentProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case AuthorizeCard(creditCardDetails, replyTo) =>
          if (creditCardDetails.startsWith("0")) replyTo ! PaymentDenied
          else replyTo ! PaymentAccepted

          Behaviors.same

        case _ => Behaviors.same
      }
    }
  }
  object FulfilmentManagerActor {

    def active(orderId: Int): Behavior[PaymentProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case DispatchOrder(item, replyTo) =>
          logWithName(context)(s"Order $orderId for item $item has been dispatched.")
          replyTo ! OrderConfirmed
          active(orderId + 1)
        case _ => Behaviors.same
      }
    }
    def apply(): Behavior[PaymentProtocol] = active(1)

  }
}
