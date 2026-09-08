package general.ui

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg as s
import general.navigation.Router
import models.*
import org.scalajs.dom
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import util.{ApiClient, Debouncer}
import com.raquo.airstream.core.Observable

object SearchBar {
  private val selectedBaseWordId = Var[Option[Long]](None)
  private val selectedBaseTagId = Var[Option[Long]](None)
  private val firstSelectedName = Var("")

  def apply(initialQuery: String = "", externalQueryStream: Observable[String] = EventStream.empty): HtmlElement = {
    val searchQuery = Var(initialQuery)
    val suggestions = Var(Seq.empty[SearchSuggestionItem])
    val isMouseOverSuggestions = Var(false)
    var inputRef: dom.html.Input = null

    // デバウンス処理（300ms）
    val debouncedHandleInput = Debouncer.debounce[String](300) { text =>
      handleInput(text, suggestions)
    }

    div(
      // --- 外部からの信号を監視して searchQuery を更新する ---
      externalQueryStream --> searchQuery,
      cls := "relative w-full max-w-md mx-auto py-2 flex items-center gap-2",

      // --- 入力フィールドコンテナ ---
      div(
        cls := "relative flex-grow",

        div(
          cls := "absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none",
          s.svg(
            s.cls := "h-5 w-5 text-gray-400",
            s.fill := "none",
            s.viewBox := "0 0 24 24",
            s.stroke := "currentColor",
            s.strokeWidth := "2",
            s.circle(s.cx := "11", s.cy := "11", s.r := "8"),
            s.line(s.x1 := "21", s.y1 := "21", s.x2 := "16.65", s.y2 := "16.65")
          )
        ),

        input(
          tpe := "text",
          cls := "block w-full pl-10 pr-10 py-2.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-full text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 shadow-sm appearance-none transition-all",
          placeholder := "タグやワードで検索",
          controlled(
            value <-- searchQuery.signal,
            onInput.mapToValue --> { v =>
              searchQuery.set(v)
              // 空文字の場合は即座にクリア（デバウンスなし）
              if (v.trim.isEmpty) {
                suggestions.set(Nil)
                selectedBaseWordId.set(None)
                selectedBaseTagId.set(None)
                firstSelectedName.set("")
              } else {
                debouncedHandleInput(v)
              }
            }
          ),
          onMountCallback(el => inputRef = el.thisNode.ref),
          onKeyDown.filter(_.key == "Enter") --> { ev =>
            ev.target.asInstanceOf[dom.html.Input].blur()
            performSearch(searchQuery.now())
            suggestions.set(Nil)
          },
          onBlur --> { _ =>
            js.timers.setTimeout(200) {
              if (!isMouseOverSuggestions.now()) {
                suggestions.set(Nil)
              }
            }
          }
        ),

        child <-- searchQuery.signal.map { q =>
          if (q.isEmpty) emptyNode
          else
            button(
              tpe := "button",
              cls := "absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors",
              onClick --> { _ =>
                searchQuery.set("")
                suggestions.set(Nil)
                selectedBaseWordId.set(None)
                selectedBaseTagId.set(None)
                if (inputRef != null) inputRef.focus()
              },
              s.svg(
                s.cls := "h-5 w-5",
                s.fill := "none",
                s.viewBox := "0 0 24 24",
                s.stroke := "currentColor",
                s.circle(s.cx := "12", s.cy := "12", s.r := "10"),
                s.path(s.d := "M15 9l-6 6M9 9l6 6")
              )
            )
        }
      ),

      button(
        tpe := "button",
        cls := "p-2.5 bg-blue-600 text-white rounded-full hover:bg-blue-700 shadow-md active:scale-95 transition-all flex-shrink-0",
        onClick --> { _ => performSearch(searchQuery.now()) },
        s.svg(
          s.cls := "h-5 w-5",
          s.fill := "none",
          s.viewBox := "0 0 24 24",
          s.stroke := "currentColor",
          s.strokeWidth := "2.5",
          s.path(s.d := "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z")
        )
      ),

      // --- サジェスト結果ドロップダウン (重複を修正済み) ---
      child <-- suggestions.signal.map { items =>
        if (items.isEmpty) emptyNode
        else
          div(
            cls := "absolute top-full left-0 right-0 mt-2 bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-700 rounded-2xl shadow-xl overflow-hidden z-50",
            onMouseEnter --> { _ => isMouseOverSuggestions.set(true) },
            onMouseLeave --> { _ => isMouseOverSuggestions.set(false) },
            items.map { item =>
              div(
                cls := "px-4 py-3 hover:bg-blue-50 dark:hover:bg-gray-800 cursor-pointer flex items-center gap-3 border-b border-gray-50 dark:border-gray-800 last:border-none",
                onMouseDown.preventDefault --> { _ =>
                  onSuggestionClick(item, searchQuery, suggestions, inputRef)
                },
                // アイコン表示
                if (item.sourceType == "tag") {
                  s.svg(
                    s.cls := "w-5 h-5 text-gray-600 dark:text-gray-400",
                    s.fill := "none",
                    s.stroke := "currentColor",
                    s.strokeWidth := "2",
                    s.viewBox := "0 0 24 24",
                    s.path(
                      s.d := "M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"
                    )
                  )
                } else {
                  s.svg(
                    s.cls := "w-5 h-5 text-gray-600 dark:text-gray-400",
                    s.fill := "none",
                    s.stroke := "currentColor",
                    s.strokeWidth := "2",
                    s.viewBox := "0 0 24 24",
                    s.path(s.d := "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z")
                  )
                },

                // テキスト表示エリアを 1 つに集約
                div(
                  cls := "flex flex-col flex-grow",
                  div(
                    cls := "flex items-center gap-1",
                    // 2段階目の検索時に「親の名前 >」を表示
                    item.baseId.map(_ =>
                      span(
                        cls := "text-[10px] font-bold text-gray-400 dark:text-gray-500",
                        s"${firstSelectedName.now()} >"
                      )
                    ),

                    // メインの名前表示（# の重複を stripPrefix で防ぐ）
                    span(
                      cls := "text-sm font-bold text-gray-700 dark:text-gray-200",
                      if (item.sourceType == "tag") s"#${item.name.stripPrefix("#")}" else item.name
                    )
                  )
                )
              )
            }
          )
      }
    )
  }

  private def handleInput(text: String, targetVar: Var[Seq[SearchSuggestionItem]]): Unit = {
    val normalizedText = text.replace("＃", "#")
    val trimmedText = normalizedText.trim
    if (text.isEmpty) {
      targetVar.set(Nil)
      selectedBaseWordId.set(None)
      selectedBaseTagId.set(None)
      firstSelectedName.set("")
      return
    }

    val hasSpace = normalizedText.exists(c => c == ' ' || c == '\u3000')

    // 【重要】IDが既にセットされている（サジェストで選択済み）か、スペースがある場合に複合検索とする
    val isAlreadySelected = selectedBaseWordId.now().isDefined || selectedBaseTagId.now().isDefined

    val req = if (hasSpace || isAlreadySelected) {
      // --- 複合検索モード ---
      val parts = normalizedText.split("[ \u3000]+", 2)
      val firstPart = parts(0)
      val subQuery = if (parts.length > 1) parts(1) else ""

      // 手入力で1段階目のパートを書き換えたらIDをリセットする
      val currentNameInInput = firstPart.stripPrefix("#")
      if (firstSelectedName.now() != currentNameInInput) {
        firstSelectedName.set(currentNameInInput)
        selectedBaseWordId.set(None)
        selectedBaseTagId.set(None)
      }

      if (firstPart.startsWith("#")) {
        val tagName = firstPart.stripPrefix("#")
        SearchSuggestRequest(
          query = subQuery,
          baseTagId = selectedBaseTagId.now(), // now() で取得
          baseTagName = Some(tagName)
        )
      } else {
        SearchSuggestRequest(
          query = subQuery,
          baseWordId = selectedBaseWordId.now(), // now() で取得
          baseWordName = Some(firstPart)
        )
      }
    } else {
      // --- 通常検索モード ---
      firstSelectedName.set("")
      SearchSuggestRequest(query = trimmedText)
    }

    ApiClient.post[SearchSuggestRequest, SearchSuggestionResponse]("/words/suggest", req).onComplete {
      case scala.util.Success(response) => targetVar.set(response.items)
      case scala.util.Failure(e)        =>
        dom.console.error(s"サジェスト取得失敗: ${e.getMessage}")
        targetVar.set(Nil)
    }
  }

  private def onSuggestionClick(
      item: SearchSuggestionItem,
      searchQuery: Var[String],
      suggestions: Var[Seq[SearchSuggestionItem]],
      inputRef: dom.html.Input
  ): Unit = {
    if (item.baseId.isEmpty) {
      // --- 1段階目の選択 ---
      val cleanName = item.name.stripPrefix("#")
      firstSelectedName.set(cleanName)

      val newQuery = if (item.sourceType == "tag") {
        selectedBaseTagId.set(Some(item.id))
        selectedBaseWordId.set(None)
        s"#$cleanName"
      } else {
        selectedBaseWordId.set(Some(item.id))
        selectedBaseTagId.set(None)
        cleanName
      }

      searchQuery.set(newQuery)

      // 【重要】スペースなしの状態でも handleInput を呼び出して
      // 即座に 2 段階目のサジェスト（関連ワード）を取得しに行く
      handleInput(newQuery, suggestions)

      if (inputRef != null) inputRef.focus()
    } else {
      // --- 2段階目の選択（検索実行） ---
      val (wId, tId) = if (item.baseType.contains("word")) {
        (Some(item.baseId.get), Some(item.id))
      } else {
        (Some(item.id), Some(item.baseId.get))
      }
      suggestions.set(Nil)
      Router.navigateTo(SearchPage(query = searchQuery.now().trim, wordId = wId, tagId = tId))
    }
  }

  private def performSearch(query: String): Unit = {
    val q = query.trim
    if (q.nonEmpty) {
      Router.navigateTo(
        SearchPage(
          query = q,
          wordId = selectedBaseWordId.now(),
          tagId = selectedBaseTagId.now()
        )
      )
    }
  }
}
