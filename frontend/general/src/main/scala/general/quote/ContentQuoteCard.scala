package general.quote

import com.raquo.laminar.api.L.*
import general.navigation.Router
import models.{WordDetailPage, WordDetailTab}
import org.scalajs.dom
import scala.scalajs.js
import shared.api.{ContentQuoteTypes, ContentQuoteViewValue}

object ContentQuoteCard {
  val DeletedMessage = "引用元は削除されました。"

  def apply(
      quote: ContentQuoteViewValue,
      onRemove: Option[() => Unit] = None,
      navigate: Boolean = true
  ): HtmlElement = {
    val canNavigate = navigate && !quote.isSourceDeleted && quote.wordId.isDefined && quote.tagId.isDefined
    val isSelectable = !navigate && onRemove.isEmpty
    val sourceLabel = if (quote.sourceContentTypeId == ContentQuoteTypes.Meaning) "語釈" else "用例"
    val author = quote.displayId.fold(quote.userName.getOrElse(""))(id => s"${quote.userName.getOrElse("")} @$id")

    div(
      dataAttr("content-quote-card") := sourceLabel,
      dataAttr("quote-state") := (if (quote.isSourceDeleted) "deleted" else "available"),
      cls := s"group relative py-2 pl-4 text-left ${
          if (canNavigate) "cursor-pointer"
          else if (isSelectable) "cursor-pointer"
          else ""
        }",
      if (canNavigate) onClick.stopPropagation --> (_ => navigateToSource(quote)) else emptyMod,
      span(
        aria.hidden := true,
        cls := s"absolute bottom-2 left-0 top-2 w-0.5 ${
            if (quote.isSourceDeleted) "bg-gray-300 dark:bg-gray-700"
            else "bg-slate-700 transition-colors group-hover:bg-orange-500 dark:bg-slate-400"
          }"
      ),
      if (quote.isSourceDeleted) {
        div(
          cls := "space-y-2 py-1",
          div(
            cls := "flex items-center gap-2 text-[10px] font-bold tracking-[0.12em] text-gray-400 dark:text-gray-500",
            span(aria.hidden := true, cls := "font-serif text-lg leading-none", "“"),
            span("引用")
          ),
          p(cls := "text-sm leading-relaxed text-gray-500 dark:text-gray-400", DeletedMessage)
        )
      } else {
        div(
          cls := "space-y-2",
          div(
            cls := "flex min-h-7 items-start justify-between gap-3",
            div(
              cls := "min-w-0",
              div(
                cls := "flex items-center gap-2 text-[10px] font-bold tracking-[0.12em] text-slate-600 dark:text-slate-300",
                span(
                  aria.hidden := true,
                  cls := "font-serif text-xl leading-none text-orange-500 dark:text-orange-400",
                  "“"
                ),
                span(s"${sourceLabel}から引用")
              ),
              quote.wordMain
                .filter(_.nonEmpty)
                .map(word =>
                  div(
                    cls := "mt-1 flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-0.5",
                    span(cls := "font-serif text-[15px] font-bold text-slate-900 dark:text-slate-100", word),
                    quote.tagName
                      .filter(_.nonEmpty)
                      .map(name => span(cls := "text-[10px] text-gray-400 dark:text-gray-500", s"#$name"))
                      .getOrElse(emptyNode)
                  )
                )
                .getOrElse(emptyNode)
            ),
            onRemove
              .map(remove =>
                button(
                  tpe := "button",
                  cls := "shrink-0 whitespace-nowrap px-1 py-1 text-[10px] font-bold text-gray-400 underline decoration-gray-300 underline-offset-4 transition-colors hover:text-red-600 hover:decoration-red-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500/50 dark:text-gray-500 dark:decoration-gray-700 dark:hover:text-red-400",
                  "引用を解除",
                  onClick.stopPropagation --> (_ => remove())
                )
              )
              .getOrElse(emptyNode)
          ),
          p(
            cls := "line-clamp-3 text-sm leading-7 text-gray-700 dark:text-gray-200",
            quote.contentText.getOrElse("")
          ),
          div(
            cls := "flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-t border-gray-100 pt-2 text-[10px] dark:border-gray-800",
            span(
              cls := "min-w-0 truncate text-gray-400 dark:text-gray-500",
              if (author.nonEmpty) s"出典 · $author" else "出典情報なし"
            ),
            if (canNavigate)
              button(
                tpe := "button",
                cls := "shrink-0 px-1 py-1 font-bold text-slate-600 underline decoration-slate-300 underline-offset-4 transition-colors group-hover:text-orange-600 group-hover:decoration-orange-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500/50 dark:text-slate-300 dark:decoration-slate-700 dark:group-hover:text-orange-400",
                "引用元を見る →",
                onClick.stopPropagation --> (_ => navigateToSource(quote))
              )
            else if (isSelectable)
              span(
                cls := "shrink-0 font-bold text-slate-500 transition-colors group-hover:text-orange-600 dark:text-slate-400 dark:group-hover:text-orange-400",
                "選択する →"
              )
            else emptyNode
          )
        )
      }
    )
  }

  private def navigateToSource(quote: ContentQuoteViewValue): Unit =
    for {
      wordId <- quote.wordId
      tagId <- quote.tagId
    } {
      val tab =
        if (quote.sourceContentTypeId == ContentQuoteTypes.Example) WordDetailTab.Example else WordDetailTab.Meaning
      val prefix = if (tab == WordDetailTab.Example) "example" else "meaning"
      Router.router.pushState(WordDetailPage(wordId, tagId, tab))
      js.timers.setTimeout(100) {
        val anchor = s"$prefix-${quote.sourceContentId}"
        dom.window.location.hash = anchor
        Option(dom.document.getElementById(anchor)).foreach(_.scrollIntoView())
      }
    }
}
