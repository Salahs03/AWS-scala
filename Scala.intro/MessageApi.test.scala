import config.AppConfig
import http.NameServer
import io.undertow.Undertow
import java.net.{HttpURLConnection, InetSocketAddress, URI}
import java.nio.charset.StandardCharsets
import queue.{MessageQueue, InMemoryMessageQueue, Message, QueueError}
import scala.util.Using

class MessageApiTests extends munit.FunSuite:

  private def withServer(
      check: String => Unit,
      messageQueue: MessageQueue = new InMemoryMessageQueue
  ): Unit =
    val app = new NameServer(
      AppConfig("Salah", "127.0.0.1"),
      messageQueue = messageQueue
    )

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

  private def post(
      url: String,
      body: Option[String] = None
  ): (Int, String, String) =
    val connection = URI.create(url).toURL
      .openConnection().asInstanceOf[HttpURLConnection]

    connection.setRequestMethod("POST")
    connection.setConnectTimeout(5000)
    connection.setReadTimeout(5000)

    try
      body.foreach { text =>
        connection.setRequestProperty(
          "Content-Type",
          "application/json"
        )
        connection.setDoOutput(true)

        Using.resource(connection.getOutputStream) { output =>
          output.write(text.getBytes(StandardCharsets.UTF_8))
        }
      }

      val status = connection.getResponseCode

      val stream =
        if status >= 400 then connection.getErrorStream
        else connection.getInputStream

      val responseBody =
        if stream == null then ""
        else
          Using.resource(
            scala.io.Source.fromInputStream(stream, "UTF-8")
          )(_.mkString)

      val requestId =
        Option(connection.getHeaderField("X-Request-Id"))
          .getOrElse("")

      (status, responseBody, requestId)
    finally
      connection.disconnect()

  private def failingQueue: MessageQueue =
    new MessageQueue:
      override def send(text: String): Either[QueueError, String] =
        Left(QueueError("Simulated queue failure"))

      override def receive(): Either[QueueError, Option[Message]] =
        Left(QueueError("Simulated queue failure"))

  test("Send returns 202, receive returns the message, then 204") {
    withServer { baseUrl =>
      val (sendStatus, sendBody, sendRequestId) =
        post(
          s"$baseUrl/api/messages",
          Some("""{"text":"Hello Salah"}""")
        )

      assertEquals(sendStatus, 202)

      val queued = ujson.read(sendBody)
      val messageId = queued("messageId").str

      assert(messageId.nonEmpty)
      assertEquals(queued("status").str, "queued")
      assert(sendRequestId.nonEmpty)

      val (readStatus, readBody, readRequestId) =
        post(s"$baseUrl/api/messages/receive")

      assertEquals(readStatus, 200)

      val received = ujson.read(readBody)

      assertEquals(received("messageId").str, messageId)
      assertEquals(received("text").str, "Hello Salah")

      java.time.Instant.parse(received("sentAt").str)

      assert(readRequestId.nonEmpty)
      assertNotEquals(sendRequestId, readRequestId)

      val (emptyStatus, emptyBody, _) =
        post(s"$baseUrl/api/messages/receive")

      assertEquals(emptyStatus, 204)
      assertEquals(emptyBody, "")
    }
  }

  test("A new empty queue returns 204 with no body") {
    withServer { baseUrl =>
      val (status, body, requestId) =
        post(s"$baseUrl/api/messages/receive")

      assertEquals(status, 204)
      assertEquals(body, "")
      assert(requestId.nonEmpty)
    }
  }

  test("Malformed JSON returns 400 and is not queued") {
    withServer { baseUrl =>
      val (status, body, requestId) =
        post(s"$baseUrl/api/messages", Some("not json"))

      assertEquals(status, 400)

      val error = ujson.read(body)("error")

      assertEquals(error("code").str, "INVALID_JSON")
      assert(requestId.nonEmpty)
      assertEquals(error("requestId").str, requestId)

      val (emptyStatus, emptyBody, _) =
        post(s"$baseUrl/api/messages/receive")

      assertEquals(emptyStatus, 204)
      assertEquals(emptyBody, "")
    }
  }

  test("Invalid text returns 400 and is not queued") {
    withServer { baseUrl =>
      val invalidBodies = Seq(
        "{}",
        """{"text":""}""",
        """{"text":"   "}""",
        """{"text":123}""",
        """{"text":null}""",
        ujson.Obj("text" -> ("a" * 501)).render()
      )

      for invalidBody <- invalidBodies do
        val (status, body, requestId) =
          post(s"$baseUrl/api/messages", Some(invalidBody))

        assertEquals(status, 400)

        val error = ujson.read(body)("error")

        assertEquals(error("code").str, "VALIDATION_FAILED")
        assert(requestId.nonEmpty)
        assertEquals(error("requestId").str, requestId)

      val (emptyStatus, emptyBody, _) =
        post(s"$baseUrl/api/messages/receive")

      assertEquals(emptyStatus, 204)
      assertEquals(emptyBody, "")
    }
  }

  test("Exactly 500 characters are accepted and preserved") {
    withServer { baseUrl =>
      val text = "a" * 500
      val requestBody = ujson.Obj("text" -> text).render()

      val (sendStatus, _, _) =
        post(s"$baseUrl/api/messages", Some(requestBody))

      assertEquals(sendStatus, 202)

      val (readStatus, readBody, _) =
        post(s"$baseUrl/api/messages/receive")

      assertEquals(readStatus, 200)
      assertEquals(ujson.read(readBody)("text").str, text)
    }
  }

  test("A queue send failure returns 500 INTERNAL_ERROR") {
    withServer(
      baseUrl => {
        val (status, body, requestId) =
          post(
            s"$baseUrl/api/messages",
            Some("""{"text":"Hello Salah"}""")
          )

        assertEquals(status, 500)

        val error = ujson.read(body)("error")

        assertEquals(error("code").str, "INTERNAL_ERROR")
        assert(requestId.nonEmpty)
        assertEquals(error("requestId").str, requestId)
        assert(!body.contains("Simulated queue failure"))
      },
      messageQueue = failingQueue
    )
  }

  test("A queue receive failure returns 500 INTERNAL_ERROR") {
    withServer(
      baseUrl => {
        val (status, body, requestId) =
          post(s"$baseUrl/api/messages/receive")

        assertEquals(status, 500)

        val error = ujson.read(body)("error")

        assertEquals(error("code").str, "INTERNAL_ERROR")
        assert(requestId.nonEmpty)
        assertEquals(error("requestId").str, requestId)
        assert(!body.contains("Simulated queue failure"))
      },
      messageQueue = failingQueue
    )
  }