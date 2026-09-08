package general.word

import com.raquo.laminar.api.L.*
import models.WordTagDetailResponse

object WordDetailHeader {
  def apply(
      header: WordTagDetailResponse,
      isGuest: Boolean,
      isFollowed: Signal[Boolean],
      copyWord: String => Unit,
      toggleFollow: () => Unit
  ): HtmlElement =
    div(
      cls := "flex items-start justify-between w-full",
      div(
        cls := "flex-1",
        div(cls := "text-xs text-gray-400 dark:text-gray-500 mb-1", header.wordReading),
        div(
          cls := "flex items-center space-x-2 mb-1",
          h1(cls := "text-2xl font-black text-gray-800 dark:text-gray-100 leading-tight", header.wordMain),
          button(
            cls := "p-1.5 text-gray-400 dark:text-gray-500 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-gray-800 rounded-lg transition active:scale-95",
            title := "言葉をコピー",
            svg.svg(
              svg.cls := "w-5 h-5",
              svg.fill := "none",
              svg.stroke := "currentColor",
              svg.strokeWidth := "2",
              svg.viewBox := "0 0 24 24",
              svg.strokeLineCap := "round",
              svg.strokeLineJoin := "round",
              svg.path(
                svg.d := "M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"
              )
            ),
            onClick --> (_ => copyWord(header.wordMain))
          )
        ),
        span(
          cls := "inline-block px-2 py-0.5 rounded bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-[10px] font-bold",
          s"#${header.tagName}"
        )
      ),
      followButton(isGuest, isFollowed, toggleFollow)
    )

  private def followButton(
      isGuest: Boolean,
      isFollowed: Signal[Boolean],
      toggleFollow: () => Unit
  ): HtmlElement =
    if (isGuest) {
      button(
        cls := "px-4 py-1.5 rounded-full font-bold text-xs transition-all flex items-center border bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400 border-gray-200 dark:border-gray-700 cursor-not-allowed",
        title := "フォローするにはログインが必要です",
        span("+ フォロー")
      )
    } else {
      button(
        cls := "px-4 py-1.5 rounded-full font-bold text-xs transition-all flex items-center border",
        cls <-- isFollowed.map {
          case true => "bg-white dark:bg-gray-900 text-blue-600 dark:text-blue-400 border-blue-200 dark:border-blue-800"
          case false => "bg-blue-600 text-white border-blue-600"
        },
        child <-- isFollowed.map {
          case true  => span("✓ フォロー中")
          case false => span("+ フォロー")
        },
        onClick --> (_ => toggleFollow())
      )
    }
}
