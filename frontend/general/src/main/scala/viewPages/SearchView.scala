package viewPages
import general.ui.PullToRefresh
import general.ui.SearchBar

import com.raquo.laminar.api.L._
import scala.scalajs.js
import general.navigation.Router
import models._
import upickle.default._
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

// タイトルとリストを同期させるための管理クラス
case class SearchState(
    displayQuery: String,
    items: Seq[SearchViewValue]
)

object SearchView {
  // 状態管理変数
  private val searchStateVar = Var(SearchState("", Seq.empty))
  private val isSearchingVar = Var(false)
  private var lastSearchKey = Option.empty[(String, Option[Long], Option[Long])]

  def apply(page: SearchPage): HtmlElement = {
    PullToRefresh(
      onRefresh = () => fetchResults(page.query, page.wordId, page.tagId),
      containerCls = "max-w-md mx-auto h-full overflow-y-auto overscroll-contain",
      contentCls = "p-4 space-y-6"
    )(
      // 検索バー
      SearchBar(
        initialQuery = page.query,
        externalQueryStream = searchStateVar.signal.map(_.displayQuery).changes
      ),

      // ヘッダー（取得完了したクエリ名を表示）
      child <-- searchStateVar.signal.map { state =>
        if (state.displayQuery.isEmpty) emptyNode
        else
          div(
            cls := "border-b border-gray-100 dark:border-gray-800 pb-2",
            span(cls := "text-sm text-gray-500 dark:text-gray-400", "検索結果: "),
            span(cls := "text-lg font-bold text-gray-800 dark:text-gray-100", s"「${state.displayQuery}」")
          )
      },

      // リスト表示エリア
      div(
        cls <-- isSearchingVar.signal.map(busy => if (busy) "opacity-50" else "opacity-100 transition-opacity"),
        child <-- isSearchingVar.signal.combineWith(searchStateVar.signal).map {
          case (true, _) =>
            // 検索中の表示（お好みでローディングスピナーなど）
            div(cls := "py-20 text-center text-gray-400", "検索しています...")

          case (false, state) =>
            // 検索が終わった後の判定
            if (state.items.nonEmpty) {
              div(cls := "space-y-3", state.items.map(renderResultItem))
            } else if (state.displayQuery.nonEmpty) {
              // 検索した結果、本当に0件だった場合のみ表示
              renderEmptyState(state.displayQuery)
            } else {
              // 最初（何も入力して検索していない状態）
              div(cls := "py-20 text-center text-gray-400", "検索ワードを入力してください")
            }
        }
      ),

      // マウント時にデータ取得を開始
      onMountCallback { _ =>
        val key = (page.query.trim, page.wordId, page.tagId)
        if (!lastSearchKey.contains(key)) {
          fetchResults(page.query, page.wordId, page.tagId)
        }
      }
    )
  }

  private def fetchResults(query: String, wordId: Option[Long] = None, tagId: Option[Long] = None): Unit = {
    val trimmedQuery = query.trim
    val searchKey = (trimmedQuery, wordId, tagId)

    if (trimmedQuery.isEmpty && wordId.isEmpty && tagId.isEmpty) {
      searchStateVar.set(SearchState("", Seq.empty))
      lastSearchKey = Some(searchKey)
      return
    }

    isSearchingVar.set(true)

    val params = List(
      Some(s"keyword=${js.URIUtils.encodeURIComponent(trimmedQuery)}"),
      wordId.map(id => s"wordId=$id"),
      tagId.map(id => s"tagId=$id")
    ).flatten.mkString("&")

    util.ApiClient
      .get[Seq[SearchViewValue]](s"/words?$params")
      .onComplete {
        case scala.util.Success(results) =>
          val displayTitle = if (results.nonEmpty) {
            (wordId, tagId) match {
              case (Some(_), Some(_)) =>
                val item = results.head
                // 元の入力が「#」で始まっていたら「#タグ 言葉」
                // そうでなければ（言葉から入力してたら）「言葉 #タグ」
                if (trimmedQuery.startsWith("#") || trimmedQuery.startsWith("＃")) {
                  s"#${item.tagName} ${item.wordMain}"
                } else {
                  s"${item.wordMain} #${item.tagName}"
                }

              case (None, Some(_)) => s"#${results.head.tagName}"
              case (Some(_), None) => results.head.wordMain
              case _               => trimmedQuery
            }
          } else {
            trimmedQuery
          }

          searchStateVar.set(SearchState(displayTitle, results))
          lastSearchKey = Some(searchKey)
          isSearchingVar.set(false)

        case scala.util.Failure(e) =>
          dom.console.error(s"Fetch error: ${e.getMessage}")
          searchStateVar.set(SearchState(trimmedQuery, Seq.empty))
          lastSearchKey = Some(searchKey)
          isSearchingVar.set(false)

          if (e.getMessage.contains("401")) {
            dom.window.location.href = "/signIn"
          }
      }
  }

  private def renderEmptyState(query: String): HtmlElement = {
    div(
      cls := "py-20 flex flex-col items-center text-gray-400 space-y-4",
      svg.svg(
        svg.cls := "w-16 h-16",
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "1.5",
        svg.viewBox := "0 0 24 24",
        svg.circle(svg.cx := "11", svg.cy := "11", svg.r := "8"),
        svg.path(svg.d := "M21 21l-4.35-4.35")
      ),
      p(s"「$query」に一致する言葉は見つかりませんでした。")
    )
  }

  private def renderResultItem(item: SearchViewValue): HtmlElement = {
    div(
      cls := "p-4 bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-800 rounded-2xl shadow-sm flex justify-between items-center hover:border-blue-300 dark:hover:border-blue-700 cursor-pointer group transition-all active:scale-[0.98]",
      onClick --> { _ =>
        Router.router.pushState(WordDetailPage(item.wordId, item.tagId))
      },
      div(
        cls := "space-y-1",
        div(cls := "text-[10px] text-gray-400 dark:text-gray-500", item.wordReading),
        div(
          cls := "text-lg font-black text-gray-800 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400",
          item.wordMain
        ),
        span(
          cls := "inline-block px-2 py-0.5 rounded bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-[10px] font-bold",
          s"#${item.tagName}"
        )
      ),
      span(
        cls := "text-gray-300 dark:text-gray-600 group-hover:text-blue-500 dark:group-hover:text-blue-400 text-xl font-bold transition-colors",
        "→"
      )
    )
  }
}
