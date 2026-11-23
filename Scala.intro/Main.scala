//> using scala "3.10.0"
//> using jvm "21"
//> using dep "com.lihaoyi::upickle:4.4.3"
//> using dep "com.lihaoyi::cask:0.11.3"
//> using dep "ch.qos.logback:logback-classic:1.6.6"
//> using dep "net.logstash.logback:logstash-logback-encoder:9.0"
//> using dep "software.amazon.awssdk:sqs:2.57.5"
//> using resourceDir "resources"
//> using test.dep "org.scalameta::munit:1.0.4"

import config.AppConfig
import http.NameServer
import metadata.{InstanceMetadata, ServerIdentity}
import queue.{InMemoryMessageQueue, MessageQueue, SqsMessageQueue}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.time.Duration

object Main:
  def main(args: Array[String]): Unit =
    val config = AppConfig.load()
    val logger = org.slf4j.LoggerFactory.getLogger("Main")
    val sqs = if config.appEnv == "aws" then
      val timeouts = ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(10))
        .apiCallAttemptTimeout(Duration.ofSeconds(4)).build()
      Some(SqsClient.builder().region(Region.of(config.awsRegion.get))
        .overrideConfiguration(timeouts).build())
    else None

    val messageQueue: MessageQueue = sqs match
      case Some(client) => new SqsMessageQueue(client, config.queueUrl.get)
      case None => new InMemoryMessageQueue

    val identity = if config.appEnv == "aws" then
      InstanceMetadata.fetch() match
        case Right(found) => found
        case Left(_) =>
          logger.info("EC2 metadata unavailable; using local server identity")
          ServerIdentity("local", "local")
    else ServerIdentity("local", "local")

    sqs.foreach(client => sys.addShutdownHook(client.close()))
    try
      val server = new NameServer(config, identity, messageQueue)
      server.main(args)
      logger.atInfo().addKeyValue("event", "app_started")
        .addKeyValue("port", server.port).addKeyValue("appEnv", config.appEnv)
        .log("Scala workshop app listening")
    catch
      case scala.util.control.NonFatal(error) =>
        sqs.foreach(_.close())
        throw error
