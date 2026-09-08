package general.ui

import com.raquo.laminar.api.L.*

object FormComponents {

  /** 必須アスタリスク付きのラベル
    */
  def renderLabel(text: String, required: Boolean): HtmlElement = {
    label(
      cls := "block text-sm font-bold text-gray-600 dark:text-gray-300 mb-2.5 ml-1 tracking-wide",
      text,
      if (required) span(cls := "ml-1 text-red-500", "*") else emptyNode
    )
  }

  /** 固定サイズのテキストエリア
    */
  def textAreaField(
      ph: String,
      state: Var[String],
      limit: Int,
      onChanged: String => Unit = _ => ()
  ): HtmlElement = textArea(
    cls := "w-full p-4 bg-gray-50 dark:bg-gray-800 border border-transparent dark:border-gray-700 rounded-2xl outline-none h-[130px] resize-none text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 leading-relaxed focus:bg-white dark:focus:bg-gray-800 focus:border-blue-200 focus:ring-4 focus:ring-blue-50 dark:focus:ring-blue-900/30 transition-all",
    placeholder := ph,
    maxLength := limit,
    controlled(
      value <-- state.signal,
      onInput.mapToValue --> { value =>
        state.set(value)
        onChanged(value)
      }
    )
  )

  /** 文字数カウンター
    */
  def charCounter(signal: Signal[String], limit: Int): HtmlElement = {
    div(
      cls := "text-right mt-1 text-[10px] tracking-tighter",
      child.text <-- signal.map(txt => s"${txt.length} / $limit"),
      cls <-- signal.map(txt =>
        if (txt.length >= limit) "text-orange-500 font-bold"
        else "text-gray-300 dark:text-gray-600"
      )
    )
  }

  /** セクションのタイトル（青い縦棒付き）
    */
  def sectionTitle(title: String): HtmlElement = div(
    cls := "flex items-center space-x-2 mb-2",
    span(cls := "w-1 h-4 bg-blue-500 rounded-full"),
    h2(cls := "text-sm font-bold text-gray-700 dark:text-gray-200", title)
  )
}
