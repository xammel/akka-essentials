package com.xammel.utils

import akka.actor.typed.scaladsl.ActorContext

object LoggingUtils {

  sealed trait LogLevel
  case object Info  extends LogLevel
  case object Error extends LogLevel

  def logWithName(context: ActorContext[_])(message: String, logLevel: LogLevel = Info): Unit = {
    import context.log

    val fullMessage = s"[${context.self.path.name}] $message"

    logLevel match {
      case Info  => log.info(fullMessage)
      case Error => log.error(fullMessage)
    }
  }
}
