import config.AppConfig
import http.NameServer
import io.undertow.Undertow
import java.net.{HttpURLConnection, InetSocketAddress, URI}

class NameApiTests extends munit.FunSuite:

  private def withServer(check: String => Unit): Unit =
    val app = new NameServer(AppConfig("Salah", "127.0.0.1"))

    val server = Undertow.builder()
      .addHttpListener(0, "127.0.0.1")
      .setHandler(app.defaultHandler)
      .build()

    try
      server.start()

      val address = server.getListenerInfo.get(0)
        .getAddress.asInstanceOf[InetSocketAddress]

      check(s"http://127.0.0.1:${address.getPort}")
    finally
      try server.stop()
      finally app.executionContext.shutdown()

  private def get(url: String): (Int, String, String) =
    val connection = URI.create(url).toURL
      .openConnection().asInstanceOf[HttpURLConnection]

    connection.setConnectTimeout(5000)
    connection.setReadTimeout(5000)

    try
      val status = connection.getResponseCode

      val stream =
        if status >= 400 then connection.getErrorStream
        else connection.getInputStream

      val body = scala.util.Using.resource(
        scala.io.Source.fromInputStream(stream)
      )(_.mkString)

      val requestId =
        Option(connection.getHeaderField("X-Request-Id")).getOrElse("")

      (status, body, requestId)
    finally
      connection.disconnect()

  test("GET /api/name returns 200, Salah and a request ID") {
    withServer { baseUrl =>
      val (status, body, requestId) = get(s"$baseUrl/api/name")

      assertEquals(status, 200)
      assertEquals(ujson.read(body)("name").str, "Salah")
      assert(requestId.nonEmpty, "Response must include X-Request-Id")
    }
  }

  test("An unknown API route returns 404, NOT_FOUND and a request ID") {
    withServer { baseUrl =>
      val (status, body, requestId) =
        get(s"$baseUrl/api/does-not-exist")

      assertEquals(status, 404)
      assertEquals(
        ujson.read(body)("error")("code").str,
        "NOT_FOUND"
      )
      assert(requestId.nonEmpty, "Response must include X-Request-Id")
    }
  }

    test("Separate requests receive different request IDs") {
    withServer { baseUrl =>
      val (_, _, firstId) = get(s"$baseUrl/api/name")
      val (_, _, secondId) = get(s"$baseUrl/api/name")

      assert(firstId.nonEmpty, "First request ID must exist")
      assert(secondId.nonEmpty, "Second request ID must exist")
      assertNotEquals(firstId, secondId)
    }
  }

  test("Homepage and JavaScript responses include request IDs") {
    withServer { baseUrl =>
      for path <- Seq("/", "/app.js") do
        val (status, _, requestId) = get(s"$baseUrl$path")

        assertEquals(status, 200)
        assert(
          requestId.nonEmpty,
          s"$path must include X-Request-Id"
        )
    }
  }