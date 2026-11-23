package http

import upickle.default.ReadWriter

case class NameResponse(name: String) derives ReadWriter

case class ErrorBody(
    code: String,
    message: String,
    requestId: String
) derives ReadWriter

case class ErrorResponse(error: ErrorBody) derives ReadWriter

case class WhoAmIResponse(
    serverId: String,
    availabilityZone: String
) derives ReadWriter

case class MessageQueuedResponse(
    messageId: String,
    status: String
) derives ReadWriter

case class MessageReceivedResponse(
    messageId: String,
    text: String,
    sentAt: String
) derives ReadWriter
