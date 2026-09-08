package general.ui

import com.raquo.laminar.api.L.*
import scala.scalajs.js

object Toast {
  private val toastVar = Var(Option.empty[String])

  /** トースト通知を表示する
    * @param message
    *   表示するメッセージ
    * @param duration
    *   表示時間（ミリ秒）デフォルト2000ms
    */
  def show(message: String, duration: Int = 2000): Unit = {
    toastVar.set(Some(message))

    js.timers.setTimeout(duration) {
      toastVar.set(None)
    }
  }

  /** トースト通知のレンダリング要素 アプリケーションのルートに配置する
    */
  def render(): HtmlElement = {
    div(
      cls := "fixed top-20 left-1/2 transform -translate-x-1/2 z-[9999] pointer-events-none",
      child <-- toastVar.signal.map {
        case Some(message) =>
          div(
            cls := "bg-gray-800 text-white px-6 py-3 rounded-full shadow-lg text-sm font-medium animate-fade-in-down",
            message
          )
        case None =>
          emptyNode
      }
    )
  }
}
