package config

case class AppConfig(
    displayName: String,
    host: String,
    appEnv: String = "local",
    awsRegion: Option[String] = None,
    queueUrl: Option[String] = None,
    notesBucket: Option[String] = None
)

object AppConfig:
  // Pure validation makes missing settings testable without exiting the test process.
  def fromEnv(env: Map[String, String]): Either[String, AppConfig] =
    def value(name: String) = env.get(name).map(_.trim).filter(_.nonEmpty)
    def required(name: String) = value(name).toRight(s"Missing required environment variable $name")
    for
      displayName <- required("DISPLAY_NAME")
      mode = value("APP_ENV").getOrElse("local")
      host = value("HOST").getOrElse("localhost")
      config <- mode match
        case "local" => Right(AppConfig(displayName, host))
        case "aws" =>
          for
            region <- required("AWS_REGION")
            queueUrl <- required("QUEUE_URL")
            bucket <- required("NOTES_BUCKET")
          yield AppConfig(displayName, host, mode, Some(region), Some(queueUrl), Some(bucket))
        case _ => Left("APP_ENV must be either local or aws")
    yield config

  def load(): AppConfig = fromEnv(sys.env) match
    case Right(config) => config
    case Left(message) =>
      org.slf4j.LoggerFactory.getLogger("AppConfig").error(message)
      throw new IllegalArgumentException(message)
