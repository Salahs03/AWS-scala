package config

case class AppConfig(displayName: String, host: String)

object AppConfig:
  def load(): AppConfig =
    val displayName =
      sys.env.get("DISPLAY_NAME").filter(_.trim.nonEmpty).getOrElse {
        org.slf4j.LoggerFactory.getLogger("NameServer")
          .error("Missing required environment variable DISPLAY_NAME")
        sys.exit(1)
      }

    val host = sys.env.get("HOST").getOrElse("localhost")

    AppConfig(displayName, host)