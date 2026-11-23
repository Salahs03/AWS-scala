package http

import upickle.default.write
import config.AppConfig
import metadata.ServerIdentity
import queue.{MessageQueue, InMemoryMessageQueue}
import notes.{NotesStore, InMemoryNotesStore, StorageFailure, InvalidStoredNote}

class NameServer(
    config: AppConfig,
    identity: ServerIdentity = ServerIdentity("local", "local"),
    messageQueue: MessageQueue = new InMemoryMessageQueue,
    notesStore: NotesStore = new InMemoryNotesStore
) extends cask.MainRoutes:

  private val appLogger =
    org.slf4j.LoggerFactory.getLogger("NameServer")

  override def host: String = config.host
  override def debugMode: Boolean = false

  private def queueApiError(
      request: cask.Request,
      status: Int,
      code: String,
      message: String
  ): cask.Response[String] =
    val requestId =
      request.exchange.getResponseHeaders.getFirst("X-Request-Id")

    cask.Response(
      write(ErrorResponse(ErrorBody(code, message, requestId))),
      statusCode = status,
      headers = Seq("Content-Type" -> "application/json")
    )

  @cask.post("/api/messages")
  def sendMessage(request: cask.Request): cask.Response[String] =
    val body = request.text()

    scala.util.Try(ujson.read(body)) match
      case scala.util.Failure(_) =>
        queueApiError(
          request,
          400,
          "INVALID_JSON",
          "Request body must be valid JSON"
        )

      case scala.util.Success(json) =>
        val text =
          json.objOpt
            .flatMap(_.get("text"))
            .flatMap(_.strOpt)

        text match
          case Some(value)
              if value.trim.nonEmpty && value.length <= 500 =>
            messageQueue.send(value) match
              case Right(messageId) =>
                appLogger.atInfo()
                  .addKeyValue("event", "message_sent")
                  .addKeyValue("messageId", messageId)
                  .addKeyValue("textLength", value.length)
                  .addKeyValue(
                    "requestId",
                    request.exchange.getResponseHeaders
                      .getFirst("X-Request-Id")
                  )
                  .log("Message queued")

                cask.Response(
                  write(MessageQueuedResponse(messageId, "queued")),
                  statusCode = 202,
                  headers = Seq(
                    "Content-Type" -> "application/json"
                  )
                )

              case Left(_) =>
                appLogger.atError()
                  .addKeyValue("event", "queue_send_failed")
                  .addKeyValue(
                    "requestId",
                    request.exchange.getResponseHeaders
                      .getFirst("X-Request-Id")
                  )
                  .log("Queue send failed")

                queueApiError(
                  request,
                  500,
                  "INTERNAL_ERROR",
                  "Unable to send message"
                )

          case _ =>
            queueApiError(
              request,
              400,
              "VALIDATION_FAILED",
              "text must be a non-blank string of at most 500 characters"
            )

  @cask.post("/api/messages/receive")
  def receiveMessage(request: cask.Request): cask.Response[String] =
    messageQueue.receive() match
      case Right(Some(message)) =>
        val ageMs = java.time.Duration.between(
          java.time.Instant.parse(message.sentAt),
          java.time.Instant.now()
        ).toMillis

        appLogger.atInfo()
          .addKeyValue("event", "message_received")
          .addKeyValue("messageId", message.messageId)
          .addKeyValue("ageMs", ageMs)
          .addKeyValue(
            "requestId",
            request.exchange.getResponseHeaders
              .getFirst("X-Request-Id")
          )
          .log("Message received")

        cask.Response(
          write(
            MessageReceivedResponse(
              message.messageId,
              message.text,
              message.sentAt
            )
          ),
          statusCode = 200,
          headers = Seq(
            "Content-Type" -> "application/json"
          )
        )

      case Right(None) =>
        cask.Response("", statusCode = 204)

      case Left(_) =>
        appLogger.atError()
          .addKeyValue("event", "queue_receive_failed")
          .addKeyValue(
            "requestId",
            request.exchange.getResponseHeaders
              .getFirst("X-Request-Id")
          )
          .log("Queue receive failed")

        queueApiError(
          request,
          500,
          "INTERNAL_ERROR",
          "Unable to receive message"
        )


  private def validNoteId(id: String): Boolean = id.matches("^[a-z0-9-]{1,64}$")

  private def noteError(request: cask.Request, error: notes.StoreError): cask.Response[String] =
    val reason = error match
      case StorageFailure(operation) => s"storage_$operation"
      case InvalidStoredNote => "invalid_stored_note"
    appLogger.atError().addKeyValue("event", "note_operation_failed")
      .addKeyValue("reason", reason)
      .addKeyValue("requestId", request.exchange.getResponseHeaders.getFirst("X-Request-Id"))
      .log("Note operation failed")
    queueApiError(request, 500, "INTERNAL_ERROR", "Unable to access note")

  @cask.put("/api/notes/:id")
  def saveNote(id: String, request: cask.Request): cask.Response[String] =
    if !validNoteId(id) then
      queueApiError(request, 400, "INVALID_NOTE_ID", "Use 1-64 lowercase letters, digits or hyphens")
    else
      scala.util.Try(ujson.read(request.text())) match
        case scala.util.Failure(_) =>
          queueApiError(request, 400, "INVALID_JSON", "Request body must be valid JSON")
        case scala.util.Success(json) =>
          json.objOpt.flatMap(_.get("text")).flatMap(_.strOpt) match
            case Some(text) if text.length > 10000 =>
              queueApiError(request, 413, "NOTE_TOO_LARGE", "text must not exceed 10000 characters")
            case Some(text) if text.trim.nonEmpty =>
              notesStore.put(id, text) match
                case Right(note) =>
                  appLogger.atInfo().addKeyValue("event", "note_saved")
                    .addKeyValue("noteId", id)
                    .addKeyValue("sizeBytes", text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .addKeyValue("requestId", request.exchange.getResponseHeaders.getFirst("X-Request-Id"))
                    .log("Note saved")
                  cask.Response(write(note), headers = Seq("Content-Type" -> "application/json"))
                case Left(error) => noteError(request, error)
            case _ => queueApiError(request, 400, "VALIDATION_FAILED", "text must be a non-blank string")

  @cask.get("/api/notes/:id")
  def readNote(id: String, request: cask.Request): cask.Response[String] =
    if !validNoteId(id) then
      queueApiError(request, 400, "INVALID_NOTE_ID", "Use 1-64 lowercase letters, digits or hyphens")
    else
      notesStore.get(id) match
        case Left(error) => noteError(request, error)
        case Right(note) =>
          appLogger.atInfo().addKeyValue("event", "note_read").addKeyValue("noteId", id)
            .addKeyValue("found", note.nonEmpty)
            .addKeyValue("requestId", request.exchange.getResponseHeaders.getFirst("X-Request-Id"))
            .log("Note read")
          note match
            case Some(value) => cask.Response(write(value), headers = Seq("Content-Type" -> "application/json"))
            case None => queueApiError(request, 404, "NOTE_NOT_FOUND", "Note not found")

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
      headers = Seq(
        "Content-Type" -> "application/json"
      )
    )

  @cask.get("/api/name")
  def giveName() =
    val response = NameResponse(config.displayName)

    cask.Response(
      write(response),
      statusCode = 200,
      headers = Seq(
        "Content-Type" -> "application/json"
      )
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

  initialize()
