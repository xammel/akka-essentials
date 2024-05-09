package com.xammel.recap

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

object ThreadModel {

  // #1 OOP encapsulation is only valid in the single threaded model

  class BankAccount(private var amount: Int) {
    override def toString: String = s"Amount: $amount"

    def withdraw(money: Int) = this.amount -= money
    def deposit(money: Int)  = this.amount += money
    def getAmount            = this.amount
  }

  val account         = new BankAccount(1000)
  val depositThreads  = (1 to 1000).map(_ => new Thread(() => account.deposit(1)))
  val withdrawThreads = (1 to 1000).map(_ => new Thread(() => account.withdraw(1)))

  def demoRace = {
    (depositThreads ++ withdrawThreads).foreach(_.start())
    Thread.sleep(1000)
    println(account.getAmount)
  }

  /*
  issues:
  - we don't know when threads are finished
  - race conditions

  solution: add syncronisation :

    def withdraw(money: Int) = synchronized{ this.amount -= money }
    def deposit(money: Int)  = synchronized{ this.amount += money }

  new issues:

  - deadlock
  - livelock
   */

  // #2 - delegating a task to a thread

  var task: Runnable = () => println("I'll be executed on another thread")
  val runningThread: Thread = new Thread(() => {
    while (true) {
      while (task == null) {
        runningThread.synchronized {
          println("[background] waiting for a task")
          runningThread.wait()
        }
      }

      task.synchronized {
        println("[background] I have a task!")
        task.run()
        task = null
      }
    }
  })

  def delegate(r: Runnable) = {
    if (task == null) {
      task = r
      runningThread.synchronized {
        runningThread.notify()
      }
    }
  }

  def demoOfDelegation = {
    runningThread.start()
    Thread.sleep(1000)
    delegate(() => println("I'm running from another thread"))
    Thread.sleep(1000)
    delegate(() => println("This should run in the background again"))
  }

  // #3 - tracing and dealing with errors is a pain in distributed & multithreaded systems

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  // sum 1 million numbers between 10 threads, parallelized

  val futures = (0 to 9)
    .map(i => BigInt(100000 * i) until BigInt(100000 * (i + 1))) // 0 - 100000, 100000 - 200000 etc.
    .map { range =>
      Future {
        // bug
        if (range.contains(BigInt(46372))) throw new RuntimeException
        else range.sum
      }
    }

  val sumFuture = Future.reduceLeft(futures)(_ + _)

  def demoFutures = sumFuture.onComplete(println)

  def main(args: Array[String]): Unit = {
    demoRace

    demoOfDelegation

    demoFutures

  }
}
