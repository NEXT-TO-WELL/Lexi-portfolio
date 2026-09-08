package general.tag

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg as s
import models.{SuggestionItem, SuggestionRequest, SuggestionResponse}
import org.scalajs.dom
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import util.{ApiClient, Debouncer}

object TagSearchInput {
  def apply(onSelect: SuggestionItem => Unit): HtmlElement = {
    val searchQuery = Var("")
    val suggestions = Var(Seq.empty[SuggestionItem])
    var inputRef: dom.html.Input = null

    // デバウンス処理（500ms）- サーバー負荷軽減のため延長
    val debouncedFetchTags = Debouncer.debounce[String](500) { query =>
      if (query.trim.nonEmpty) fetchTags(query, suggestions)
      else suggestions.set(Nil)
    }

    div(
      cls := "relative w-full max-w-md mx-auto",

      // --- 入力フィールド ---
      input(
        tpe := "text",
        cls := "block w-full pl-10 pr-10 py-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-full text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 shadow-sm appearance-none",
        placeholder := "追加したいタグを検索...",
        value <-- searchQuery.signal,
        onInput.mapToValue --> { v =>
          searchQuery.set(v)
          debouncedFetchTags(v)
        },
        onMountCallback(el => inputRef = el.thisNode.ref),
        // フォーカスが外れたら閉じる
        onBlur --> { _ => js.timers.setTimeout(200)(suggestions.set(Nil)) }
      ),

      // --- 左側のアイコン ---
      div(
        cls := "absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none",
        s.svg(
          s.cls := "h-5 w-5 text-gray-400",
          s.fill := "none",
          s.viewBox := "0 0 24 24",
          s.stroke := "currentColor",
          s.path(s.strokeWidth := "2", s.d := "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z")
        )
      ),

      // --- サジェストドロップダウン ---
      child <-- suggestions.signal.map { items =>
        if (items.isEmpty) emptyNode
        else
          div(
            cls := "absolute top-full left-0 right-0 mt-2 bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-700 rounded-2xl shadow-xl overflow-hidden z-50",
            items.map { item =>
              div(
                cls := "px-4 py-3 hover:bg-blue-50 dark:hover:bg-gray-800 cursor-pointer flex justify-between items-center border-b border-gray-50 dark:border-gray-800 last:border-none",
                onMouseDown.preventDefault --> { _ =>
                  onSelect(item)
                  searchQuery.set("")
                  suggestions.set(Nil)
                },
                div(
                  cls := "flex flex-col",
                  span(cls := "text-sm font-bold text-gray-700 dark:text-gray-200", s"#${item.name}"),
                  span(cls := "text-[9px] text-gray-400 dark:text-gray-500", "Tag")
                ),
                span(cls := "text-xs font-bold text-blue-500", "＋ 追加")
              )
            }
          )
      }
    )
  }

  private def fetchTags(query: String, targetVar: Var[Seq[SuggestionItem]]): Unit = {
    ApiClient
      .post[SuggestionRequest, SuggestionResponse](
        "/suggests/tags",
        SuggestionRequest(query = query)
      )
      .onComplete {
        case scala.util.Success(res) => targetVar.set(res.items)
        case _                       => targetVar.set(Nil)
      }
  }
}
