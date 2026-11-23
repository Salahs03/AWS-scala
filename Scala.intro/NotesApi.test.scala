import config.AppConfig
import http.NameServer
import notes.*
import runtime.ServerRuntime
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

class NotesApiTests extends munit.FunSuite:
  private def withServer(store: NotesStore = new InMemoryNotesStore)(check: String => Unit): Unit =
    val app = new NameServer(AppConfig("Salah", "127.0.0.1"), notesStore = store)
    val server = new ServerRuntime(app.defaultHandler, "127.0.0.1", 0, () => app.executionContext.shutdown())
    try
      server.start()
      check(s"http://127.0.0.1:${server.boundPort}")
    finally server.stop()

  private def request(base: String, id: String, body: Option[String] = None) =
    val builder = HttpRequest.newBuilder(URI.create(s"$base/api/notes/$id")).timeout(Duration.ofSeconds(5))
    body.foreach(value => builder.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(value)))
    val client = HttpClient.newHttpClient()
    try client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    finally client.close()

  private def assertError(response: HttpResponse[String], status: Int, code: String): Unit =
    assertEquals(response.statusCode(), status)
    val error = ujson.read(response.body())("error")
    assertEquals(error("code").str, code)
    val id = response.headers().firstValue("X-Request-Id").orElse("")
    assert(id.nonEmpty)
    assertEquals(error("requestId").str, id)

  test("Save, retrieve and overwrite preserve text and UTC timestamp") {
    withServer() { base =>
      for text <- Seq("Hello <script>world</script> é", "Replacement") do
        val saved = request(base, "first-note", Some(ujson.Obj("text" -> text).render()))
        assertEquals(saved.statusCode(), 200)
        val note = ujson.read(saved.body())
        assertEquals(note("id").str, "first-note")
        assertEquals(note("text").str, text)
        java.time.Instant.parse(note("savedAt").str)
        val loaded = request(base, "first-note")
        assertEquals(loaded.statusCode(), 200)
        assertEquals(ujson.read(loaded.body()), note)
    }
  }

  test("Missing note is 404, not a storage error") {
    withServer()(base => assertError(request(base, "missing"), 404, "NOTE_NOT_FOUND"))
  }

  test("GET and PUT reject invalid IDs before accessing storage") {
    withServer() { base =>
      for id <- Seq("Uppercase", "has_underscore", "x" * 65, "%2E%2E", "%2Fbad") do
        assertError(request(base, id), 400, "INVALID_NOTE_ID")
        assertError(request(base, id, Some("{}")), 400, "INVALID_NOTE_ID")
    }
  }

  test("Invalid JSON and invalid text are rejected without saving") {
    withServer() { base =>
      assertError(request(base, "note", Some("not-json")), 400, "INVALID_JSON")
      for body <- Seq("{}", "null", "[]", "{\"text\":null}", "{\"text\":4}", "{\"text\":\"   \"}") do
        assertError(request(base, "note", Some(body)), 400, "VALIDATION_FAILED")
      assertError(request(base, "note"), 404, "NOTE_NOT_FOUND")
    }
  }

  test("10000 characters accepted, 10001 rejected without overwriting") {
    withServer() { base =>
      val boundary = "é" * 10000
      assertEquals(request(base, "a" * 64, Some(ujson.Obj("text" -> boundary).render())).statusCode(), 200)
      assertError(request(base, "a" * 64, Some(ujson.Obj("text" -> (boundary + "x")).render())), 413, "NOTE_TOO_LARGE")
      assertEquals(ujson.read(request(base, "a" * 64).body())("text").str, boundary)
    }
  }

  test("Storage failures become generic 500 responses with request IDs") {
    val failing = new NotesStore:
      def put(id: String, text: String) = Left(StorageFailure("secret internal detail"))
      def get(id: String) = Left(InvalidStoredNote)
    withServer(failing) { base =>
      for response <- Seq(request(base, "note"), request(base, "note", Some("{\"text\":\"hello\"}"))) do
        assertError(response, 500, "INTERNAL_ERROR")
        assert(!response.body().contains("secret"))
    }
  }
