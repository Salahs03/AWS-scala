import config.AppConfig
import metadata.{ServerMetadata, ServerIdentity, MetadataError}

class ConfigurationTests extends munit.FunSuite:
  test("Local mode needs only DISPLAY_NAME and ignores AWS settings") {
    assertEquals(AppConfig.fromEnv(Map("DISPLAY_NAME" -> "Salah")), Right(AppConfig("Salah", "localhost")))
    assert(AppConfig.fromEnv(Map.empty).isLeft)
    assert(AppConfig.fromEnv(Map("DISPLAY_NAME" -> " ")).isLeft)
    assert(AppConfig.fromEnv(Map("DISPLAY_NAME" -> "Salah", "APP_ENV" -> "typo")).isLeft)
  }
  test("AWS mode validates each required setting") {
    val env = Map("DISPLAY_NAME" -> "Salah", "APP_ENV" -> "aws", "AWS_REGION" -> "eu-west-2",
      "QUEUE_URL" -> "https://sqs.eu-west-2.amazonaws.com/123/example", "NOTES_BUCKET" -> "test-notes")
    assert(AppConfig.fromEnv(env).isRight)
    for key <- Seq("DISPLAY_NAME", "AWS_REGION", "QUEUE_URL", "NOTES_BUCKET") do
      assert(AppConfig.fromEnv(env - key).left.toOption.get.contains(key))
      assert(AppConfig.fromEnv(env.updated(key, "  ")).isLeft)
  }
  test("ECS metadata takes precedence over EC2") {
    val task = "{\"TaskARN\":\"arn:aws:ecs:eu-west-2:123:task/cluster/task123\",\"AvailabilityZone\":\"eu-west-2b\"}"
    val identity = ServerMetadata.resolve(Map("APP_ENV" -> "aws", "ECS_CONTAINER_METADATA_URI_V4" -> "http://metadata/v4/id"),
      url => { assertEquals(url, "http://metadata/v4/id/task"); Right(task) },
      () => fail("EC2 must not be called after successful ECS metadata"))
    assertEquals(identity, ServerIdentity("task123", "eu-west-2b"))
  }
  test("Missing or broken ECS metadata falls back to EC2, then local") {
    assert(ServerMetadata.parseTask("{}").isLeft)
    val aws = Map("APP_ENV" -> "aws", "ECS_CONTAINER_METADATA_URI_V4" -> "http://metadata")
    assertEquals(ServerMetadata.resolve(aws, _ => Right("invalid"), () => Right(ServerIdentity("i-example", "eu-west-2a"))),
      ServerIdentity("i-example", "eu-west-2a"))
    assertEquals(ServerMetadata.resolve(aws, _ => Left(MetadataError("offline")), () => Left(MetadataError("offline"))),
      ServerIdentity("local", "local"))
  }
  test("Local mode never contacts cloud metadata") {
    assertEquals(ServerMetadata.resolve(Map.empty, _ => fail("Unexpected ECS request"), () => fail("Unexpected EC2 request")),
      ServerIdentity("local", "local"))
  }
