package com.xammel.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.xammel.utils.LoggingUtils.{Error, logWithName}

import scala.collection.mutable.{Map => MutableMap}

object BreakingActorEncapsulation {

  // naive bank account
  sealed trait AccountCommand
  case class Deposit(cardId: String, amount: Double)  extends AccountCommand
  case class Withdraw(cardId: String, amount: Double) extends AccountCommand
  case class CreateCreditCard(cardId: String)         extends AccountCommand
  case object CheckCardStatuses                       extends AccountCommand

  sealed trait CreditCardCommand
  case class AttachToAccount(balances: MutableMap[String, Double], cards: MutableMap[String, ActorRef[CreditCardCommand]]) extends CreditCardCommand
  case object CheckStatus                                                                                                  extends CreditCardCommand

  /*
  NEVER PASS MUTABLE STATE TO OTHER ACTORS.
  NEVER PASS THE CONTEXT REFERENCE TO OTHER ACTORS.
  Same for Futures.
   */

  object NaiveBankAccount {
    def apply(): Behavior[AccountCommand] = Behaviors.setup { context =>
      val accountBalances: MutableMap[String, Double]              = MutableMap()
      val cardMap: MutableMap[String, ActorRef[CreditCardCommand]] = MutableMap()

      Behaviors.receiveMessage {
        case CreateCreditCard(cardId) =>
          logWithName(context)(s"Creating credit card $cardId")
          // create credit card child
          val creditCardRef = context.spawn(CreditCard(cardId), cardId)
          // give a referral bonus
          accountBalances += cardId -> 10
          // send an AttachToAccount message to child
          creditCardRef ! AttachToAccount(accountBalances, cardMap)
          // change behaviour
          Behaviors.same
        case Deposit(cardId, amount) =>
          val oldBalance: Double = accountBalances.getOrElse(cardId, 0)
          logWithName(context)(s"Depositing $amount via card $cardId, balance on card: ${oldBalance + amount}")
          accountBalances += cardId -> (oldBalance + amount)
          Behaviors.same
        case Withdraw(cardId, amount) =>
          val oldBalance: Double = accountBalances.getOrElse(cardId, 0)
          if (oldBalance < amount) {
            logWithName(context)(s"Balance $oldBalance not sufficient to withdraw $amount", Error)
          } else {
            logWithName(context)(s"Withdrawing $amount via card $cardId, balance on card: ${oldBalance - amount}")
            accountBalances += cardId -> (oldBalance - amount)
          }
          Behaviors.same

        case CheckCardStatuses =>
          logWithName(context)(s"Checking all card statuses")
          cardMap.values.foreach(_ ! CheckStatus)
          Behaviors.same
      }
    }
  }

  object CreditCard {
    def apply(cardId: String): Behavior[CreditCardCommand] = Behaviors.receive { (ctx, message) =>
      message match {
        case AttachToAccount(balances, cards) =>
          logWithName(ctx)(s"Attaching to bank account")
          balances += cardId -> 0
          cards += cardId    -> ctx.self
          Behaviors.same
        case CheckStatus =>
          logWithName(ctx)(s"All things good")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian: Behavior[Unit] = Behaviors.setup { ctx =>
      val bankAccount = ctx.spawn(NaiveBankAccount(), "BankAccount")

      bankAccount ! CreateCreditCard("gold")
      bankAccount ! CreateCreditCard("premium")
      bankAccount ! Deposit("gold", 1000)
      bankAccount ! CheckCardStatuses

      Behaviors.empty

    }
    val system = ActorSystem(userGuardian, "DemoNaiveBankAccount")
    Thread.sleep(1000)
    system.terminate()
  }
}
