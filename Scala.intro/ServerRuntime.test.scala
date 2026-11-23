import runtime.ServerRuntime
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import io.undertow.server.HttpHandler

class ServerRuntimeTests extends munit.FunSuite:
  test("Shutdown drains an active request before closing app resources") {
    val entered = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    val closed = new AtomicBoolean(false)
    val handler: HttpHandler = exchange => exchange.dispatch(new Runnable:
      def run(): Unit =
        entered.countDown()
        if !release.await(5, TimeUnit.SECONDS) then throw new RuntimeException("Test deadline")
        exchange.getResponseSender.send("finished")
    )
    val server = new ServerRuntime(handler, "127.0.0.1", 0, () => closed.set(true))
    val client = HttpClient.newHttpClient()
    server.start()
    val url = s"http://127.0.0.1:${server.boundPort}/"
    val response = client.sendAsync(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    assert(entered.await(5, TimeUnit.SECONDS))
    val stopping = java.util.concurrent.CompletableFuture.runAsync(() => server.stop(5000))
    try
      // The outstanding exchange keeps shutdown from completing.
      intercept[java.util.concurrent.TimeoutException](stopping.get(100, TimeUnit.MILLISECONDS))
      assert(!closed.get())
      release.countDown()
      assertEquals(response.get(5, TimeUnit.SECONDS).body(), "finished")
      stopping.get(5, TimeUnit.SECONDS)
      assert(closed.get())
      server.stop() // safe to call more than once
    finally
      release.countDown()
      server.stop()
      client.close()
  }
