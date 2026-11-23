package runtime

import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.handlers.GracefulShutdownHandler
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

// Own the HTTP lifecycle so resources close AFTER requests drain, not in competing hooks.
class ServerRuntime(handler: HttpHandler, host: String, port: Int, closeResources: () => Unit):
  private val logger = org.slf4j.LoggerFactory.getLogger("ServerRuntime")
  private val stopped = new AtomicBoolean(false)
  private val graceful = new GracefulShutdownHandler(handler)
  private val server = Undertow.builder().addHttpListener(port, host).setHandler(graceful).build()

  def start(): Unit = server.start()
  def boundPort: Int = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort

  def stop(timeoutMs: Long = 20000): Unit =
    if stopped.compareAndSet(false, true) then
      logger.atInfo().addKeyValue("event", "app_stopping").log("Draining HTTP requests")
      try
        graceful.shutdown()
        if !graceful.awaitShutdown(timeoutMs) then
          logger.warn("HTTP drain deadline reached")
      finally
        try server.stop()
        finally
          closeResources()
          logger.atInfo().addKeyValue("event", "app_stopped").log("Application stopped")
