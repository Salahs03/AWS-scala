package metadata

import java.net.{HttpURLConnection, URI}
import scala.util.{Try, Using}

object ServerMetadata:
  private val local = ServerIdentity("local", "local")

  def resolve(
      env: Map[String, String],
      read: String => Either[MetadataError, String] = readUrl,
      ec2: () => Either[MetadataError, ServerIdentity] = () => InstanceMetadata.fetch()
  ): ServerIdentity =
    // Local development never needs to contact the AWS metadata service.
    if env.getOrElse("APP_ENV", "local").trim != "aws" then local
    else
      val ecs = for
        uri <- env.get("ECS_CONTAINER_METADATA_URI_V4").filter(_.nonEmpty)
          .toRight(MetadataError("ECS metadata unavailable"))
        body <- read(uri.stripSuffix("/") + "/task")
        identity <- parseTask(body)
      yield identity
      ecs.orElse(ec2()).getOrElse(local)

  def parseTask(body: String): Either[MetadataError, ServerIdentity] =
    Try {
      val json = ujson.read(body)
      val arn = json("TaskARN").str
      val id = arn.split("/").last
      val zone = json("AvailabilityZone").str
      require(arn.startsWith("arn:") && arn.contains(":task/") && id.nonEmpty && zone.nonEmpty)
      ServerIdentity(id, zone)
    }.toEither.left.map(_ => MetadataError("Invalid ECS task metadata"))

  private def readUrl(url: String): Either[MetadataError, String] =
    Try {
      val connection = URI.create(url).toURL.openConnection().asInstanceOf[HttpURLConnection]
      try
        connection.setConnectTimeout(1000)
        connection.setReadTimeout(1000)
        connection.setInstanceFollowRedirects(false)
        require(connection.getResponseCode == 200)
        Using.resource(scala.io.Source.fromInputStream(connection.getInputStream, "UTF-8"))(_.mkString)
      finally connection.disconnect()
    }.toEither.left.map(_ => MetadataError("Unable to read ECS metadata"))
