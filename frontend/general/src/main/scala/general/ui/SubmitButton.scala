package general.ui

import com.raquo.laminar.api.L.*

object SubmitButton {

  def apply(
      isLoading: Signal[Boolean],
      isValid: Signal[Boolean],
      label: String,
      // 引数名を onClick から action に変更して衝突を避ける
      action: () => Unit
  ): HtmlElement = {
    footerTag(
      cls := "fixed bottom-[calc(4rem+env(safe-area-inset-bottom))] left-0 right-0 px-4 pb-4 max-w-md mx-auto z-40 pointer-events-none",
      button(
        tpe := "button",
        cls := "pointer-events-auto",
        disabled <-- Signal.combine(isLoading, isValid).map { (loading, valid) =>
          loading || !valid
        },
        cls <-- Signal.combine(isLoading, isValid).map { (loading, valid) =>
          val base =
            "pointer-events-auto w-full py-4 rounded-2xl font-bold shadow-xl transition-all flex items-center justify-center "
          if (loading) base + "bg-blue-400 dark:bg-blue-500 text-white cursor-wait"
          else if (valid)
            base + "bg-blue-600 dark:bg-blue-500 text-white active:scale-95 shadow-blue-200/70 dark:shadow-black/30 hover:bg-blue-700 dark:hover:bg-blue-600"
          else
            base + "bg-gray-100/95 dark:bg-gray-800/95 text-gray-400 dark:text-gray-500 cursor-not-allowed shadow-gray-200/70 dark:shadow-black/20"
        },
        child <-- isLoading.map { loading =>
          if (loading) span(cls := "animate-pulse", "送信中...")
          else span(label)
        },
        // 標準の onClick (EventProp) を使い、中身で action() を呼ぶ
        onClick --> { _ => action() }
      )
    )
  }
}
