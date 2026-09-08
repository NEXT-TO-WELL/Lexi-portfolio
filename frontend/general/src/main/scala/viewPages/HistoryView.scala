package viewPages
import general.ui.PullToRefresh

import com.raquo.laminar.api.L.*
import general.navigation.Router
import models.*
import util.ApiClient
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

object HistoryView {
  private val historyVar = Var(Seq.empty[ViewHistoryViewValue])
  private val isFetching = Var(false)
  private val searchQuery = Var("")
  private var historyLoaded = false

  def apply(): HtmlElement = {
    div(
      cls := "w-full h-full min-h-0 flex flex-col bg-gray-50 dark:bg-gray-950 overflow-hidden",
      div(cls := "flex-none h-14 w-full"),

      div(
        cls := "flex-none bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 shadow-sm",
        div(
          cls := "px-5 py-4",
          h1(
            cls := "text-lg font-black text-gray-800 dark:text-gray-100 flex items-center gap-2",
            svg.svg(
              svg.cls := "w-6 h-6 text-gray-700 dark:text-gray-300",
              svg.fill := "none",
              svg.stroke := "currentColor",
              svg.strokeWidth := "2",
              svg.viewBox := "0 0 24 24",
              svg.path(svg.d := "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z")
            ),
            "閲覧履歴"
          )
        ),
        // 検索バー
        div(cls := "px-4 pb-3", renderSearchBar())
      ),

      PullToRefresh(() => fetchHistory())(
        child <-- isFetching.signal.combineWith(historyVar.signal, searchQuery.signal).map {
          case (loading, items, query) =>
            if (loading && items.isEmpty) renderLoading()
            else if (items.isEmpty) renderEmptyState(false)
            else {
              // 1. 検索フィルター
              val filtered = filterHistory(items, query)

              if (filtered.isEmpty) {
                renderEmptyState(true)
              } else {
                // 2. 日付ごとにグループ化 (YYYY/MM/DD)
                val grouped = filtered
                  .groupBy { item =>
                    val d = new scala.scalajs.js.Date(item.viewDate.toDouble)
                    s"${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}"
                  }
                  .toSeq
                  .sortBy(_._1)(Ordering[String].reverse) // 新しい日付順

                // 3. グループごとにレンダリング
                div(
                  cls := "p-4 pb-6 space-y-8",
                  grouped.map { case (dateStr, dayItems) =>
                    renderDateSection(dateStr, dayItems)
                  }
                )
              }
            }
        }
      ),

      onMountCallback { _ =>
        if (!historyLoaded) fetchHistory()
      }
    )
  }

  // --- 日付セクションのレンダリング ---
  private def renderDateSection(dateStr: String, items: Seq[ViewHistoryViewValue]): HtmlElement = {
    div(
      cls := "space-y-3",
      // 日付の見出し
      div(
        cls := "sticky top-0 z-10 py-1 bg-gray-50/80 dark:bg-gray-950/80 backdrop-blur-sm",
        span(cls := "text-2xl font-black text-gray-800 dark:text-gray-100 tracking-tighter", dateStr)
      ),
      // その日のカード一覧
      div(cls := "space-y-3", items.sortBy(_.viewDate)(Ordering[Long].reverse).map(renderHistoryItem))
    )
  }

  private def renderHistoryItem(item: ViewHistoryViewValue): HtmlElement = {
    val detailPage = WordDetailPage(item.wordId, item.tagId)
    val timeStr = {
      val d = new scala.scalajs.js.Date(item.viewDate.toDouble)
      f"${d.getHours().toInt}%02d:${d.getMinutes().toInt}%02d"
    }

    div(
      cls := "bg-white dark:bg-gray-900 p-4 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-800 flex flex-col gap-2 active:scale-[0.98] transition-all cursor-pointer hover:border-blue-100 dark:hover:border-blue-700",
      onClick --> { _ => Router.router.pushState(detailPage) },

      div(
        cls := "flex justify-between items-start",
        div(
          cls := "flex flex-col gap-1",
          span(cls := "text-[10px] font-bold text-gray-400 dark:text-gray-500", item.wordReading),
          span(cls := "text-base font-black text-gray-800 dark:text-gray-100", item.wordMain),
          span(
            cls := "text-[10px] font-bold text-blue-600 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 rounded w-fit mt-1",
            s"#${item.tagName}"
          )
        ),
        // セクションが日付を持っているので、カード内は時刻のみ表示
        span(cls := "text-[10px] text-gray-300 font-bold", timeStr)
      )
    )
  }

  private def fetchHistory(): Unit = {
    isFetching.set(true)
    ApiClient.get[ViewHistoryResponse]("/user/history").onComplete {
      case scala.util.Success(res) =>
        isFetching.set(false)
        historyLoaded = true
        historyVar.set(res.viewHistories)
      case scala.util.Failure(e) =>
        isFetching.set(false)
        historyLoaded = true
        dom.console.error(s"Fetch failed: ${e.getMessage}")
    }
  }

  private def renderSearchBar(): HtmlElement = {
    div(
      cls := "relative",
      input(
        tpe := "text",
        cls := "w-full px-4 py-2 pl-10 pr-10 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
        placeholder := "履歴を検索...",
        value <-- searchQuery.signal,
        onInput.mapToValue --> searchQuery
      ),
      // 検索アイコン
      svg.svg(
        svg.cls := "absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none",
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 24 24",
        svg.path(svg.d := "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z")
      ),
      // クリアボタン
      child <-- searchQuery.signal.map { query =>
        if (query.nonEmpty) {
          button(
            cls := "absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600 transition",
            onClick --> { _ => searchQuery.set("") },
            svg.svg(
              svg.cls := "w-4 h-4",
              svg.fill := "none",
              svg.stroke := "currentColor",
              svg.strokeWidth := "2",
              svg.viewBox := "0 0 24 24",
              svg.path(svg.d := "M6 18L18 6M6 6l12 12")
            )
          )
        } else emptyNode
      }
    )
  }

  private def filterHistory(items: Seq[ViewHistoryViewValue], query: String): Seq[ViewHistoryViewValue] = {
    if (query.trim.isEmpty) items
    else {
      val q = query.toLowerCase.trim
      items.filter { item =>
        item.wordMain.toLowerCase.contains(q) ||
        item.wordReading.toLowerCase.contains(q) ||
        item.tagName.toLowerCase.contains(q)
      }
    }
  }

  private def renderLoading() = div(
    cls := "py-20 text-center",
    div(cls := "animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent inline-block")
  )

  private def renderEmptyState(isSearchResult: Boolean) = {
    val message = if (isSearchResult) "検索結果が見つかりませんでした" else "履歴はまだありません"
    div(cls := "py-24 text-center text-gray-400", message)
  }
}
