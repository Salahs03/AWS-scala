//> using dep "com.lihaoyi::upickle:4.4.3"
//> using dep "com.lihaoyi::cask:0.11.3"
//> using dep "ch.qos.logback:logback-classic:1.6.6"
//> using dep "net.logstash.logback:logstash-logback-encoder:9.0"
//> using resourceDir "resources"
//> using test.dep "org.scalameta::munit:1.0.4"

import config.AppConfig
import http.NameServer
import metadata.{InstanceMetadata, ServerIdentity}

object Main:
  def main(args: Array[String]): Unit =
    val config = AppConfig.load()
    val logger = org.slf4j.LoggerFactory.getLogger("Main")

    val identity = InstanceMetadata.fetch() match
      case Right(found) =>
        found
      case Left(_) =>
        logger.info("EC2 metadata unavailable; using local server identity")
        ServerIdentity("local", "local")

    val server = new NameServer(config, identity)
    server.main(args)