package com.xammel.infra

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.{Config, ConfigFactory}

object AkkaConfig {

  object SimpleLoggingActor {
    def apply(): Behavior[String] = Behaviors.receive { (context, message) =>
      context.log.info(message)
      Behaviors.same
    }
  }

  // inline configuration

  def demoInlineConfig: Unit = {
    // HOCON style
    val configString =
      """
        | akka {
        |   loglevel = "DEBUG"
        | }
        |""".stripMargin

    val config: Config = ConfigFactory.parseString(configString)
    val system         = ActorSystem(SimpleLoggingActor(), "ConfigDemo", config = config)

    system ! "A message to remember"

    Thread.sleep(1000)
    system.terminate()
  }

  // config file
  def demoConfigFile: Unit = {

    val specialConfig = ConfigFactory.load.getConfig("myConfig2") // or myConfig

    val specialSystem = ActorSystem(SimpleLoggingActor(), "ConfigDemo", config = specialConfig)

    specialSystem ! "a message here hi"

    Thread.sleep(1000)
    specialSystem.terminate()

  }

  // file different to application.conf

  def demoSeperateConfigFile: Unit = {

    val configFile    = ConfigFactory.load("secret-config/secretConfiguration.conf")
    val specialConfig = configFile.getString("akka.logLevel") // or myConfig

    println(specialConfig)
  }

  // different file formats

  def demoOtherFileFormats = {
    val jsonConfig = ConfigFactory.load("config.json")
    println(jsonConfig)
    println(jsonConfig.getString("aJsonProperty"))
    println(jsonConfig.getString("akka.logLevel"))

    // properties format also supported eg. resources/config.properties
  }

  def main(args: Array[String]): Unit = {
//    demoInlineConfig
//    demoConfigFile
//    demoSeperateConfigFile
    demoOtherFileFormats
  }

}
