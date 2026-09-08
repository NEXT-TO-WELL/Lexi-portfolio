package util

import component.{Modal, ModalType}
import org.scalajs.dom
import scala.concurrent.Future
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global

object ErrorHandler {

  /** API呼び出しのエラーを統一的に処理するヘルパー
    *
    * @param future
    *   API呼び出しのFuture
    * @param onSuccess
    *   成功時のコールバック
    * @param customErrorHandler
    *   カスタムエラー処理（オプション）。Noneの場合はデフォルト処理
    * @param showSuccessModal
    *   成功時にモーダルを表示するか（デフォルト: false）
    * @param successMessage
    *   成功時のメッセージ
    */
  def handle[T](
      future: Future[T],
      onSuccess: T => Unit,
      customErrorHandler: Option[Throwable => Unit] = None,
      showSuccessModal: Boolean = false,
      successMessage: String = "操作が完了しました"
  ): Unit = {
    future.onComplete {
      case Success(result) =>
        onSuccess(result)
        if (showSuccessModal) {
          Modal.show("成功", successMessage, ModalType.Info)
        }

      case Failure(error) =>
        customErrorHandler match {
          case Some(handler) => handler(error)
          case None          => handleError(error)
        }
    }
  }

  /** エラーを適切に処理し、ユーザーにフィードバックを提供
    */
  def handleError(error: Throwable): Unit = {
    error match {
      case e: UnauthorizedError =>
        // 401エラーは既にApiClientでリダイレクトされているが、念のため
        dom.console.error(s"Unauthorized: ${e.message}")
      // リダイレクトは ApiClient で行われるため、ここでは何もしない

      case e: ForbiddenError =>
        Modal.show(
          "アクセス拒否",
          e.message,
          ModalType.Info
        )

      case e: NotFoundError =>
        Modal.show(
          "見つかりません",
          e.message,
          ModalType.Info
        )

      case e: BadRequestError =>
        Modal.show(
          "入力エラー",
          e.message,
          ModalType.Info
        )

      case e: ServerError =>
        Modal.show(
          "サーバーエラー",
          "サーバーで問題が発生しました。しばらくしてから再度お試しください。",
          ModalType.Info
        )
        dom.console.error(s"Server Error: ${e.message}")

      case e: NetworkError =>
        Modal.show(
          "接続エラー",
          "ネットワーク接続を確認してください。",
          ModalType.Info
        )
        dom.console.error(s"Network Error: ${e.message}")

      case e =>
        // その他の予期しないエラー
        Modal.show(
          "エラー",
          "予期しないエラーが発生しました。",
          ModalType.Info
        )
        dom.console.error("Unexpected error:", e)
    }
  }

  /** APIエラーを特定の型としてキャスト（型安全性向上のため）
    */
  def isApiError(error: Throwable): Boolean = error.isInstanceOf[ApiError]

  /** エラーがリトライ可能かどうかを判定
    */
  def isRetryable(error: Throwable): Boolean = error match {
    case _: NetworkError                                             => true
    case e: ServerError if e.statusCode >= 500 && e.statusCode < 600 => true
    case _                                                           => false
  }
}
