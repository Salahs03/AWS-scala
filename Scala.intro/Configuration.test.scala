import config.AppConfig

class ConfigurationTests extends munit.FunSuite:
  test("Local mode needs only DISPLAY_NAME and ignores AWS settings") {
    assertEquals(AppConfig.fromEnv(Map("DISPLAY_NAME" -> "Salah")), Right(AppConfig("Salah", "localhost")))
    assert(AppConfig.fromEnv(Map.empty).isLeft)
    assert(AppConfig.fromEnv(Map("DISPLAY_NAME" -> " ")).isLeft)
    assert(AppConfig.fromEnv(Map("DISPLAY_NAME" -> "Salah", "APP_ENV" -> "typo")).isLeft)
  }
  test("AWS mode validates each required setting") {
    val env = Map("DISPLAY_NAME" -> "Salah", "APP_ENV" -> "aws", "AWS_REGION" -> "eu-west-2",
      "QUEUE_URL" -> "https://sqs.eu-west-2.amazonaws.com/123/example")
    assert(AppConfig.fromEnv(env).isRight)
    for key <- Seq("DISPLAY_NAME", "AWS_REGION", "QUEUE_URL") do
      assert(AppConfig.fromEnv(env - key).left.toOption.get.contains(key))
      assert(AppConfig.fromEnv(env.updated(key, "  ")).isLeft)
  }
