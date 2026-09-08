package util

import scala.concurrent.Future
import org.scalajs.dom
import org.scalajs.dom.{HttpMethod, RequestInit}
import upickle.default.*
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import general.auth.AuthStorage
import shared.api.ErrorResponse

// カスタムエラー型
sealed trait ApiError extends Exception {
  def message: String
  def statusCode: Int
  override def getMessage: String = message
}

case class UnauthorizedError(message: String = "認証が必要です") extends ApiError {
  val statusCode = 401
}

case class ForbiddenError(message: String = "アクセス権限がありません") extends ApiError {
  val statusCode = 403
}

case class NotFoundError(message: String = "リソースが見つかりません") extends ApiError {
  val statusCode = 404
}

case class BadRequestError(message: String) extends ApiError {
  val statusCode = 400
}

case class ServerError(message: String = "サーバーエラーが発生しました") extends ApiError {
  val statusCode = 500
}

case class NetworkError(message: String = "ネットワークエラーが発生しました") extends ApiError {
  val statusCode = 0
}

object ApiClient {

  private def getCsrfToken: String = {
    Option(dom.document.querySelector("meta[name='csrf-token']"))
      .map(_.getAttribute("content"))
      .getOrElse("")
  }

  private def buildHeaders(): js.Dictionary[String] = {
    val baseHeaders = js.Dictionary(
      "Content-Type" -> "application/json; charset=utf-8",
      "Csrf-Token" -> getCsrfToken
    )
    val token = dom.window.localStorage.getItem("auth_token")
    if (token != null && token.nonEmpty) {
      baseHeaders("Authorization") = s"Bearer $token"
    }
    baseHeaders
  }

  def post[Req: Writer, T: Reader](endpoint: String, requestBody: Req): Future[T] = {
    dom
      .fetch(
        endpoint,
        new RequestInit {
          method = HttpMethod.POST
          body = write(requestBody)
          headers = buildHeaders()
        }
      )
      .toFuture
      .flatMap(handleResponse[T])
      .recoverWith(handleNetworkError)
  }

  def postRaw[T: Reader](endpoint: String, jsonBody: String): Future[T] = {
    dom
      .fetch(
        endpoint,
        new RequestInit {
          method = HttpMethod.POST
          body = jsonBody
          headers = buildHeaders()
        }
      )
      .toFuture
      .flatMap(handleResponse[T])
      .recoverWith(handleNetworkError)
  }

  def postRawIgnore(endpoint: String, jsonBody: String): Future[Unit] = {
    dom
      .fetch(
        endpoint,
        new RequestInit {
          method = HttpMethod.POST
          body = jsonBody
          headers = buildHeaders()
        }
      )
      .toFuture
      .flatMap { res =>
        if (res.ok) Future.successful(())
        else handleErrorStatus(res.status, "")
      }
      .recoverWith(handleNetworkError)
  }

  def get[T: Reader](endpoint: String): Future[T] = {
    dom
      .fetch(
        endpoint,
        new RequestInit {
          method = HttpMethod.GET
          headers = buildHeaders()
        }
      )
      .toFuture
      .flatMap(handleResponse[T])
      .recoverWith(handleNetworkError)
  }

  def delete(endpoint: String): Future[Unit] = {
    dom
      .fetch(
        endpoint,
        new RequestInit {
          method = HttpMethod.DELETE
          headers = buildHeaders()
        }
      )
      .toFuture
      .flatMap { res =>
        if (res.ok) Future.successful(())
        else res.text().toFuture.flatMap(text => handleErrorStatus(res.status, text))
      }
      .recoverWith(handleNetworkError)
  }

  def deleteWithBody[Req: Writer, T: Reader](endpoint: String, requestBody: Req): Future[T] = {
    dom
      .fetch(
        endpoint,
        new RequestInit {
          method = HttpMethod.DELETE
          body = write(requestBody)
          headers = buildHeaders()
        }
      )
      .toFuture
      .flatMap(handleResponse[T])
      .recoverWith(handleNetworkError)
  }

  def put[Req: Writer, T: Reader](endpoint: String, requestBody: Req): Future[T] = {
    dom
      .fetch(
        endpoint,
        new RequestInit {
          method = HttpMethod.PUT
          body = write(requestBody)
          headers = buildHeaders()
        }
      )
      .toFuture
      .flatMap(handleResponse[T])
      .recoverWith(handleNetworkError)
  }

  private def handleResponse[T: Reader](res: dom.Response): Future[T] = {
    res.text().toFuture.flatMap { text =>
      if (res.ok) {
        try {
          val data = if (text.trim.isEmpty) "[]" else text
          Future.successful(read[T](data))
        } catch {
          case e: Exception =>
            dom.console.error(s"--- JSONパース失敗詳細 ---")
            dom.console.error(s"リクエストURL: ${res.url}")
            dom.console.error(s"エラーメッセージ: ${e.getMessage}")
            dom.console.error(s"実際にサーバーから届いたデータ: $text")
            dom.console.error(s"--------------------------")
            Future.failed(e)
        }
      } else {
        handleErrorStatus(res.status, text)
      }
    }
  }

  private def handleErrorStatus[T](status: Int, responseText: String): Future[T] = {
    // サーバーからのErrorResponseを解析
    val errorMessage = try {
      val errorResp = read[ErrorResponse](responseText)
      errorResp.message
    } catch {
      case _: Exception => responseText
    }

    val error: ApiError = status match {
      case 400 => BadRequestError(if (errorMessage.nonEmpty) errorMessage else "不正なリクエストです")
      case 401 =>
        // 認証情報が無効なため、ストレージをクリアしてリロード
        AuthStorage.clearAuth()
        dom.window.location.reload()
        UnauthorizedError(if (errorMessage.nonEmpty) errorMessage else "認証情報が無効です。ページをリロードします。")
      case 403           => ForbiddenError(if (errorMessage.nonEmpty) errorMessage else "アクセス権限がありません")
      case 404           => NotFoundError(if (errorMessage.nonEmpty) errorMessage else "リソースが見つかりません")
      case s if s >= 500 => ServerError(if (errorMessage.nonEmpty) errorMessage else "サーバーエラーが発生しました")
      case _             => ServerError(s"HTTP Error $status: $errorMessage")
    }

    dom.console.error(s"API Error [${error.statusCode}]: ${error.message}")
    Future.failed(error)
  }

  private def handleNetworkError[T]: PartialFunction[Throwable, Future[T]] = {
    case e: ApiError  => Future.failed(e) // 既にApiErrorの場合はそのまま
    case e: Exception =>
      dom.console.error(s"Network Error: ${e.getMessage}")
      Future.failed(NetworkError(s"ネットワークエラー: ${e.getMessage}"))
  }
}
