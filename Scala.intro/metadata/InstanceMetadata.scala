package metadata

import java.net.{HttpURLConnection, URI}
import scala.util.{Try, Using}

case class ServerIdentity(
    serverId: String,
    availabilityZone: String
)

case class MetadataError(message: String)

object InstanceMetadata:
  private val baseUrl = "http://169.254.169.254/latest"

  def fetch(): Either[MetadataError, ServerIdentity] =
    for
      token <- request(
        "PUT",
        "/api/token",
        "X-aws-ec2-metadata-token-ttl-seconds",
        "60"
      )
      serverId <- request(
        "GET",
        "/meta-data/instance-id",
        "X-aws-ec2-metadata-token",
        token
      )
      zone <- request(
        "GET",
        "/meta-data/placement/availability-zone",
        "X-aws-ec2-metadata-token",
        token
      )
    yield ServerIdentity(serverId, zone)

  private def request(
      method: String,
      path: String,
      header: String,
      value: String
  ): Either[MetadataError, String] =
    Try {
      val connection = URI.create(baseUrl + path).toURL
        .openConnection().asInstanceOf[HttpURLConnection]

      try
        connection.setRequestMethod(method)
        connection.setRequestProperty(header, value)
        connection.setConnectTimeout(1000)
        connection.setReadTimeout(1000)
        connection.setInstanceFollowRedirects(false)

        val status = connection.getResponseCode
        if status != 200 then
          throw new java.io.IOException(s"Metadata returned HTTP $status")

        val body = Using.resource(
          scala.io.Source.fromInputStream(connection.getInputStream)
        )(_.mkString.trim)

        if body.isEmpty then
          throw new java.io.IOException("Metadata returned an empty response")

        body
      finally
        connection.disconnect()
    }.toEither.left.map(error =>
      MetadataError(
        Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
      )
    )