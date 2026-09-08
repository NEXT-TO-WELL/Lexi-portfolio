package viewPages
import general.ui.PullToRefresh
import component.{Modal, ModalType}

import com.raquo.laminar.api.L.*
import general.mypage.FollowedPostsApi
import general.navigation.Router
import models.*
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

object FollowedWordsView {
  private val wordsVar = Var(Seq.empty[FollowItemViewValue])
  private val isFetching = Var(false)
  private var wordsLoaded = false

  def apply(): HtmlElement = {
    div(
      cls := "w-full h-full min-h-0 flex flex-col bg-gray-50 dark:bg-gray-950 overflow-hidden",
      div(cls := "flex-none h-14 w-full"),

      // --- サマリー・プログレスエリア ---
      div(
        cls := "flex-none bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 z-20 shadow-sm",
        div(
          cls := "h-0.5 bg-blue-500 transition-all duration-300",
          width <-- isFetching.signal.map(if (_) "100%" else "0%"),
          opacity <-- isFetching.signal.map(if (_) "1" else "0")
        ),
        div(
          cls := "px-5 py-3 flex items-center justify-between",
          span(cls := "text-[11px] font-black text-gray-400 uppercase tracking-widest", "Followed List"),
          child <-- wordsVar.signal.map { items =>
            span(
              cls := "text-sm font-black text-blue-600 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/30 px-4 py-1 rounded-full border border-blue-100 dark:border-blue-800 shadow-sm",
              s"${items.size} 件の言葉"
            )
          }
        )
      ),

      // --- コンテンツエリア ---
      PullToRefresh(() => fetchWords())(
        div(
          cls := "p-4 pb-6",
          child <-- isFetching.signal.combineWith(wordsVar.signal).map { case (loading, items) =>
            if (loading && items.isEmpty) renderLoading()
            else if (items.isEmpty) renderEmptyState()
            else div(cls := "grid gap-3", items.map(renderWordItem))
          }
        )
      ),

      onMountCallback { _ =>
        if (!wordsLoaded) fetchWords()
      }
    )
  }

  private def renderWordItem(item: FollowItemViewValue): HtmlElement = {
    val wordId = item.wordId.getOrElse(0L)
    val wordMain = item.wordMain.getOrElse("不明な言葉")
    val displayDate = new js.Date(item.followDate.toDouble).toLocaleDateString()
    val detailPage = WordDetailPage(wordId, item.tagId)

    div(
      // カード自体の onClick は削除。hover時の視覚効果のみ保持
      cls := "bg-white dark:bg-gray-900 p-4 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 space-y-3 relative transition-all hover:border-blue-100 dark:hover:border-blue-700",

      // 1. フォロー解除ボタン（右上固定）
      button(
        tpe := "button",
        cls := "absolute top-4 right-2 p-2 text-gray-300 dark:text-gray-600 hover:text-red-500 dark:hover:text-red-400 active:bg-red-50 dark:active:bg-red-900/20 rounded-full transition-all active:scale-90",
        onClick --> { _ =>
          Modal.show(
            title = "フォロー解除",
            message = s"「$wordMain」のフォローを解除しますか？",
            ModalType.Confirm,
            unfollow(wordId, item.tagId)
          )
        },
        svg.svg(
          svg.cls := "w-5 h-5",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2.5",
          svg.viewBox := "0 0 24 24",
          svg.circle(svg.cx := "12", svg.cy := "12", svg.r := "9"),
          svg.path(svg.d := "M9 12h6")
        )
      ),

      // 2. テキストコンテンツ（単語名のみクリック可能）
      div(
        cls := "flex flex-col pr-8",
        // 単語名：クリックで詳細へ
        span(
          cls := "text-base font-black text-gray-800 dark:text-gray-100 cursor-pointer hover:text-blue-600 dark:hover:text-blue-400 hover:underline decoration-2 underline-offset-4 transition w-fit",
          wordMain,
          onClick --> { _ => Router.router.pushState(detailPage) }
        ),
        // タグ：現状はテキスト表示のみ
        span(
          cls := "text-[10px] font-bold text-blue-600 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/30 w-fit px-2 py-0.5 rounded mt-1",
          s"#${item.tagName}"
        )
      ),

      // 3. フッター（日付と詳細ボタン）
      div(
        cls := "flex items-center justify-between pt-2 border-t border-gray-50 dark:border-gray-800",
        span(
          cls := "text-[10px] text-gray-400 font-medium flex items-center gap-1",
          svg.svg(
            svg.cls := "w-3 h-3",
            svg.fill := "none",
            svg.stroke := "currentColor",
            svg.strokeWidth := "2",
            svg.viewBox := "0 0 24 24",
            svg.path(
              svg.d := "M9 11H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2zm2-7h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z"
            )
          ),
          s"$displayDate にフォロー"
        ),

        // 詳細を見るボタン：明示的な遷移トリガー
        button(
          cls := "text-[11px] font-bold text-blue-600 dark:text-blue-400 active:opacity-50 flex items-center gap-1",
          onClick --> { _ => Router.router.pushState(detailPage) },
          "詳細を見る",
          span(cls := "text-sm", "→")
        )
      )
    )
  }

  // --- 共通パーツ & ロジック ---

  private def renderLoading() = div(
    cls := "py-20 text-center",
    div(cls := "inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent")
  )

  private def renderEmptyState(): HtmlElement = {
    div(
      cls := "py-20 text-center flex flex-col items-center px-6",
      div(
        cls := "w-20 h-20 bg-white dark:bg-gray-900 rounded-full flex items-center justify-center shadow-sm mb-6",
        svg.svg(
          svg.cls := "w-12 h-12 text-gray-400",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.viewBox := "0 0 24 24",
          svg.path(
            svg.d := "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
          )
        )
      ),
      h2(cls := "text-lg font-bold text-gray-900 dark:text-gray-100 mb-2", "フォロー中の言葉はありません"),
      p(cls := "text-sm text-gray-400 leading-relaxed mb-8", "気になる言葉をフォローして、\n自分だけの辞書を充実させましょう。"),
      button(
        cls := "w-full max-w-xs py-4 bg-blue-600 text-white rounded-2xl font-bold shadow-lg shadow-blue-200 dark:shadow-none active:scale-95 transition",
        "トレンドを見る",
        onClick --> { _ => Router.router.pushState(HomePage) }
      )
    )
  }

  private def fetchWords(): Unit = {
    isFetching.set(true)
    FollowedPostsApi.fetchWords().onComplete { res =>
      isFetching.set(false)
      wordsLoaded = true
      res.foreach(r => wordsVar.set(r.wordTagFollows))
    }
  }

  private def unfollow(wordId: Long, tagId: Long): Unit = {
    FollowedPostsApi.toggleWordFollow(wordId, tagId).onComplete { res =>
      res.foreach { _ =>
        wordsVar.update(_.filterNot(i => i.wordId.contains(wordId) && i.tagId == tagId))
      }
    }
  }
}
