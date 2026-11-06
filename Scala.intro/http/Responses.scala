package http

import upickle.default.ReadWriter

case class NameResponse(name: String) derives ReadWriter

case class ErrorBody(
    code: String,
    message: String,
    requestId: String
) derives ReadWriter

case class ErrorResponse(error: ErrorBody) derives ReadWriter