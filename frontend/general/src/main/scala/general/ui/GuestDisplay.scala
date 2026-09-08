package general.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom

object GuestDisplay {
  def apply(message: String): HtmlElement = {
    div(
      cls := "w-full text-center py-20 px-4",
      div(
        cls := "w-16 h-16 bg-gray-300 dark:bg-gray-700 rounded-full flex items-center justify-center text-gray-500 dark:text-gray-400 font-bold text-xl shadow-inner mx-auto",
        "?"
      ),
      h2(cls := "mt-4 text-xl font-bold text-gray-700 dark:text-gray-300", "ログインが必要です"),
      p(cls := "mt-1 text-sm text-gray-500 dark:text-gray-400", message),
      div(
        cls := "mt-8",
        button(
          cls := "w-full max-w-xs mx-auto bg-blue-600 dark:bg-blue-500 hover:bg-blue-700 dark:hover:bg-blue-600 text-white font-bold py-3 px-4 rounded-lg shadow-lg transition",
          onClick --> { _ => dom.window.location.href = "/signIn" },
          "ログイン / 新規登録"
        )
      )
    )
  }
}
