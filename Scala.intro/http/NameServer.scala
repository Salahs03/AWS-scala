package http

import upickle.default.write
import config.AppConfig
import metadata.ServerIdentity

class NameServer(
    config: AppConfig,
    identity: ServerIdentity = ServerIdentity("local", "local")
) extends cask.MainRoutes:

  private val appLogger =
    org.slf4j.LoggerFactory.getLogger("NameServer")

  override def host: String = config.host

  @cask.get("/health")
  def health() =
    cask.Response(
      """{"status":"ok"}""",
      statusCode = 200,
      headers = Seq(
        "Content-Type" -> "application/json",
        "Cache-Control" -> "no-store"
      )
    )

  @cask.get("/api/whoami")
  def whoAmI() =
    val response = WhoAmIResponse(
      identity.serverId,
      identity.availabilityZone
    )

    cask.Response(
      write(response),
      statusCode = 200,
      headers = Seq("Content-Type" -> "application/json")
    )

  @cask.get("/api/name")
  def giveName() =
    val response = NameResponse(config.displayName)

    cask.Response(
      write(response),
      statusCode = 200,
      headers = Seq("Content-Type" -> "application/json")
    )

  @cask.get("/")
  def homePage() =
    val html = scala.util.Using.resource(
      scala.io.Source.fromResource("index.html")
    )(_.mkString)

    cask.Response(
      html,
      headers = Seq(
        "Content-Type" -> "text/html; charset=utf-8"
      )
    )

  @cask.get("/app.js")
  def appScript() =
    val js = scala.util.Using.resource(
      scala.io.Source.fromResource("app.js")
    )(_.mkString)

    cask.Response(
      js,
      headers = Seq(
        "Content-Type" -> "text/javascript; charset=utf-8"
      )
    )

  override def handleEndpointError(
      routes: cask.main.Routes,
      endpoint: cask.router.EndpointMetadata[?],
      error: cask.router.Result.Error,
      request: cask.Request
  ): cask.Response[cask.Response.Data] =
    error match
      case cask.router.Result.Error.Exception(cause) =>
        val requestId =
          request.exchange.getResponseHeaders.getFirst("X-Request-Id")
          
        appLogger.atError()
          .setCause(cause)
          .addKeyValue("event", "request_failed")
          .addKeyValue("requestId", requestId)
          .log("Request failed")

        val body = ErrorResponse(
          ErrorBody(
            "INTERNAL_ERROR",
            "Something went wrong",
            requestId
          )
        )

        cask.Response(
          write(body),
          statusCode = 500,
          headers = Seq(
            "Content-Type" -> "application/json",
            "X-Request-Id" -> requestId
          )
        )

      case other =>
        super.handleEndpointError(routes, endpoint, other, request)

  override def handleNotFound(
      request: cask.Request
  ): cask.Response[cask.Response.Data] =
    val path = request.exchange.getRequestPath

    if path.startsWith("/api/") then
      val requestId =
        request.exchange.getResponseHeaders.getFirst("X-Request-Id")

      val message =
        s"No route for ${request.exchange.getRequestMethod} $path"

      val error = ErrorResponse(
        ErrorBody("NOT_FOUND", message, requestId)
      )

      cask.Response(
        write(error),
        statusCode = 404,
        headers = Seq(
          "Content-Type" -> "application/json",
          "X-Request-Id" -> requestId
        )
      )
    else
      super.handleNotFound(request)

  override def defaultHandler: io.undertow.server.HttpHandler =
    val routes = super.defaultHandler

    exchange =>
      val requestId =
        Option(exchange.getRequestHeaders.getFirst("X-Amzn-Trace-Id"))
          .flatMap { header =>
            header.split(";").iterator
              .map(_.trim)
              .find(_.startsWith("Root="))
              .map(_.stripPrefix("Root="))
          }
          .filter(_.nonEmpty)
          .getOrElse(java.util.UUID.randomUUID().toString)

      exchange.getResponseHeaders.put(
        new io.undertow.util.HttpString("X-Request-Id"),
        requestId
      )

      val started = System.nanoTime()

      exchange.addExchangeCompleteListener { (done, next) =>
        try
          val entry =
            if done.getRequestPath == "/health" then
              appLogger.atDebug()
            else
              appLogger.atInfo()

          entry
            .addKeyValue("event", "http_request")
            .addKeyValue("method", done.getRequestMethod.toString)
            .addKeyValue("path", done.getRequestPath)
            .addKeyValue("status", done.getStatusCode)
            .addKeyValue(
              "durationMs",
              (System.nanoTime() - started) / 1000000L
            )

          val id =
            done.getResponseHeaders.getFirst("X-Request-Id")

          if id != null then
            entry.addKeyValue("requestId", id)

          entry.log("Request finished")
        finally
          next.proceed()
      }

      routes.handleRequest(exchange)

  appLogger.atInfo()
    .addKeyValue("event", "app_started")
    .addKeyValue("port", port)
    .log("Name server starting")

  initialize()