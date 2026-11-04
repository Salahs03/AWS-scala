//> using dep "com.lihaoyi::upickle:4.4.3"
//> using dep "com.lihaoyi::cask:0.11.3"
//> using dep "ch.qos.logback:logback-classic:1.6.6"
//> using dep "net.logstash.logback:logstash-logback-encoder:9.0"
//> using resourceDir "resources"

import upickle.default.{ReadWriter, write}

case class NameResponse(name: String) derives ReadWriter
case class ErrorBody(code: String, message: String, requestId: String) derives ReadWriter
case class ErrorResponse(error: ErrorBody) derives ReadWriter

object NameServer extends cask.MainRoutes:
  @cask.get("/api/name")
  def giveName() =
    val requestId = java.util.UUID.randomUUID().toString
    val response = NameResponse("Salah")
    cask.Response(
      write(response),
      headers = Seq(
        "Content-Type" -> "application/json",
        "X-Request-Id" -> requestId
      )
    )

  @cask.get("/")
  def homePage() =
    val html = scala.util.Using.resource(
      scala.io.Source.fromResource("index.html")
    )(_.mkString)
    cask.Response(
      html,
      headers = Seq("Content-Type" -> "text/html; charset=utf-8")
    )

  @cask.get("/app.js")
  def appScript() =
    val js = scala.util.Using.resource(
      scala.io.Source.fromResource("app.js")
    )(_.mkString)
    cask.Response(
      js,
      headers = Seq("Content-Type" -> "text/javascript; charset=utf-8")
    )

  override def handleNotFound(request: cask.Request): cask.Response[cask.Response.Data] =
    val path = request.exchange.getRequestPath
    if path.startsWith("/api/") then
      val requestId = java.util.UUID.randomUUID().toString
      val message = s"No route for ${request.exchange.getRequestMethod} $path"
      val error = ErrorResponse(ErrorBody("NOT_FOUND", message, requestId))
      println(s"Missing API route: $path | status=404 | requestId=$requestId")
      cask.Response(
        write(error),
        statusCode = 404,
        headers = Seq("Content-Type" -> "application/json", "X-Request-Id" -> requestId)
      )
    else super.handleNotFound(request)

  private val appLogger = org.slf4j.LoggerFactory.getLogger("NameServer")
  appLogger.atInfo()
    .addKeyValue("event", "app_started")
    .addKeyValue("port", port)
    .log("Name server starting")  

  override def defaultHandler: io.undertow.server.HttpHandler =
    val routes = super.defaultHandler
    exchange =>
      val started = System.nanoTime()
      exchange.addExchangeCompleteListener { (done, next) =>
        try
          val entry = appLogger.atInfo()
            .addKeyValue("event", "http_request")
            .addKeyValue("method", done.getRequestMethod.toString)
            .addKeyValue("path", done.getRequestPath)
            .addKeyValue("status", done.getStatusCode)
            .addKeyValue("durationMs", (System.nanoTime() - started) / 1000000L)

          val id = done.getResponseHeaders.getFirst("X-Request-Id")
          if id != null then entry.addKeyValue("requestId", id)
          entry.log("Request finished")
        finally next.proceed()
      }
      routes.handleRequest(exchange)

  initialize()