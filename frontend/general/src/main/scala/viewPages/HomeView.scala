package viewPages
import general.ui.PullToRefresh
import general.ui.SearchBar

import com.raquo.laminar.api.L.*
import general.navigation.Router
import models.*
import util.ApiClient
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

// ホーム（トレンド）画面（仮）
object HomeView {
  private val trendsVar = Var(Option.empty[TrendResponse])
  // 通信中かどうかを管理するフラグ
  private val isFetching = Var(false)
  private var lastFetchedAt: Double = 0.0
  private val ThrottleThreshold: Double = 3000.0 // 3000ミリ秒（3秒）間は連打を無視する

  def apply(): HtmlElement = {
    PullToRefresh(
      onRefresh = () => refresh(),
      containerCls = "w-full h-full min-h-0 overflow-y-auto overscroll-contain md:h-auto md:overflow-visible",
      contentCls = "w-full mx-auto space-y-6 bg-white dark:bg-gray-900 min-h-full pt-14 pb-6 px-4 md:px-6"
    )(
      SearchBar(),
      h2(cls := "text-xl font-bold text-gray-800 dark:text-gray-200", "今日のトレンド"),

      child <-- trendsVar.signal.map {
        case None      => div(cls := "text-center py-20 text-gray-400", "読み込み中...")
        case Some(res) => renderTrendContent(res)
      },

      onMountCallback { _ =>
        // 画面が表示された際、中身が空なら取得する
        if (trendsVar.now().isEmpty) {
          fetchTrends()
        }
      }
    )
  }

  // 外部（Tabボタンなど）から強制更新するためのメソッド
  def refresh(): Unit = {
    fetchTrends()
  }

  private def fetchTrends(): Unit = {
    val now = dom.window.performance.now()
    // 1. ガード節：早期リターン
    if (isFetching.now()) return
    if (trendsVar.now().isDefined && (now - lastFetchedAt < ThrottleThreshold)) {
      dom.console.log("連打防止中")
      return
    }
    isFetching.set(true)

    // 2. 通信開始
    // onComplete の戻り値は Unit なので、fetchTrends 自体の戻り値と一致します
    ApiClient.get[TrendResponse]("/trends").onComplete {
      case scala.util.Success(res) =>
        trendsVar.set(Some(res))
        lastFetchedAt = dom.window.performance.now()
        isFetching.set(false)

      case scala.util.Failure(e) =>
        isFetching.set(false)
        if (e.getMessage.contains("401")) {
          dom.window.location.href = "/signIn"
        } else {
          dom.console.error(s"Fetch failed: ${e.getMessage}")
        }
    }
  }

  // --- renderTrendContent / renderTrendGroup は既存のまま ---
  private def renderTrendContent(res: TrendResponse): HtmlElement = {
    div(
      cls := "space-y-4",
      // 上段: BUZZ, HOT, NEW を3カラム
      div(
        cls := "grid grid-cols-1 md:grid-cols-3 gap-4",
        renderTrendGroup(
          "BUZZ",
          res.buzz,
          "bg-[#e8eef3] text-[#17324d] dark:bg-[#17324d] dark:text-[#f3f6fa]"
        ),
        renderTrendGroup(
          "HOT",
          res.hot,
          "bg-[#f8e9df] text-[#a84f2f] dark:bg-[#5a3426] dark:text-[#f1b28d]"
        ),
        renderTrendGroup(
          "NEW",
          res.news,
          "bg-[#edf0e5] text-[#526447] dark:bg-[#344036] dark:text-[#c5d0b7]"
        )
      ),
      // 下段: RANDOM を単独で表示
      renderTrendGroup(
        "RANDOM",
        res.random,
        "bg-[#f3eee3] text-[#6d604e] dark:bg-[#403a31] dark:text-[#d9cbb6]"
      )
    )
  }

  def renderTrendGroup(label: String, items: Seq[TrendViewValue], badgeCls: String): HtmlElement = {
    val tooltipText = label match {
      case "BUZZ"   => "閲覧数が多い単語"
      case "HOT"    => "コメント数が多い単語"
      case "NEW"    => "最近投稿された単語"
      case "RANDOM" => "ランダムに選ばれた単語"
      case _        => ""
    }

    div(
      cls := "bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 rounded-2xl shadow-sm overflow-hidden",
      // ヘッダー
      div(
        cls := "px-4 py-3 border-b border-gray-100 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50",
        span(
          cls := s"text-xs px-2 py-1 rounded font-bold $badgeCls cursor-help relative group",
          title := tooltipText,
          label,
          // ツールチップ
          span(
            cls := "invisible group-hover:visible absolute left-0 top-full mt-1 px-2 py-1 bg-gray-800 text-white text-xs rounded whitespace-nowrap z-10",
            tooltipText
          )
        )
      ),
      // リスト
      div(
        cls := "divide-y divide-gray-50 dark:divide-gray-700",
        items.map { item =>
          div(
            cls := "px-3 py-2.5 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer flex items-center space-x-2 group transition-colors",
            onClick --> { _ =>
              val targetTab = if (label == "HOT") WordDetailTab.Thread else WordDetailTab.Meaning
              Router.router.pushState(WordDetailPage(item.wordId, item.tagId, initialTab = targetTab))
            },
            span(
              cls := "text-base font-black text-gray-300 dark:text-gray-500 group-hover:text-blue-500 dark:group-hover:text-blue-400 w-5 text-center flex-shrink-0",
              s"${item.rank}"
            ),
            div(
              cls := "flex-1 min-w-0",
              div(
                cls := "text-sm font-bold text-gray-800 dark:text-gray-200 group-hover:text-blue-600 dark:group-hover:text-blue-400 truncate",
                item.wordName
              ),
              div(cls := "text-[10px] text-gray-400 dark:text-gray-500 truncate", s"#${item.tagName}")
            ),
            label match {
              case "RANDOM" => emptyNode
              case _        =>
                div(
                  cls := "text-xs text-gray-500 dark:text-gray-400 text-right flex-shrink-0 font-medium",
                  label match {
                    case "HOT"  => s"${item.count}件"
                    case "BUZZ" => s"${item.count}回"
                    case "NEW"  => s"${item.count}件"
                    case _      => s"${item.count}"
                  }
                )
            }
          )
        }
      )
    )
  }
}
