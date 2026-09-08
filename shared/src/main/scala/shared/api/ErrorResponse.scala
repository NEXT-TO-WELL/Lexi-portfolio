package shared.api

import upickle.default.ReadWriter

case class ErrorResponse(message: String, code: String = "ERROR") derives ReadWriter
