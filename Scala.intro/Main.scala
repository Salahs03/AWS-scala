//> using scala "3.10.0"
//> using jvm "21"
//> using dep "com.lihaoyi::upickle:4.4.3"
//> using dep "com.lihaoyi::cask:0.11.3"
//> using dep "ch.qos.logback:logback-classic:1.6.6"
//> using dep "net.logstash.logback:logstash-logback-encoder:9.0"
//> using dep "software.amazon.awssdk:sqs:2.57.5"
//> using dep "software.amazon.awssdk:s3:2.57.5"
//> using resourceDir "resources"
//> using test.dep "org.scalameta::munit:1.0.4"

import config.AppConfig
import http.NameServer
import metadata.ServerMetadata
import notes.{InMemoryNotesStore, NotesStore, S3NotesStore}
import queue.{InMemoryMessageQueue, MessageQueue, SqsMessageQueue}
import runtime.ServerRuntime
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.s3.S3Client
import java.time.Duration

object Main:
  def main(args: Array[String]): Unit =
    val config = AppConfig.load()
    val logger = org.slf4j.LoggerFactory.getLogger("Main")
    val clients = scala.collection.mutable.ArrayBuffer.empty[AutoCloseable]
    var app: Option[NameServer] = None
    def cleanup(): Unit =
      app.foreach(_.executionContext.shutdown())
      clients.reverseIterator.foreach { client =>
        try client.close()
        catch case scala.util.control.NonFatal(_) => logger.warn("AWS client cleanup failed")
      }

    try
      val stores: (MessageQueue, NotesStore) = if config.appEnv == "aws" then
        val region = Region.of(config.awsRegion.get)
        val timeouts = ClientOverrideConfiguration.builder()
          .apiCallTimeout(Duration.ofSeconds(10))
          .apiCallAttemptTimeout(Duration.ofSeconds(4)).build()
        val sqs = SqsClient.builder().region(region).overrideConfiguration(timeouts).build()
        clients += sqs
        val s3 = S3Client.builder().region(region).overrideConfiguration(timeouts).build()
        clients += s3
        (new SqsMessageQueue(sqs, config.queueUrl.get), new S3NotesStore(s3, config.notesBucket.get))
      else (new InMemoryMessageQueue, new InMemoryNotesStore)

      val (queue, notes) = stores
      val routes = new NameServer(config, ServerMetadata.resolve(sys.env), queue, notes)
      app = Some(routes)
      val server = new ServerRuntime(routes.defaultHandler, config.host, routes.port, () => cleanup())
      server.start()
      sys.addShutdownHook(server.stop())
      logger.atInfo().addKeyValue("event", "app_started")
        .addKeyValue("port", server.boundPort).addKeyValue("appEnv", config.appEnv)
        .log("Scala workshop app listening")
    catch
      case scala.util.control.NonFatal(error) =>
        cleanup()
        throw error
