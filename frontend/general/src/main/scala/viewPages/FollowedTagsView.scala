package viewPages
import general.ui.PullToRefresh
import component.{Modal, ModalType}

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg as s
import general.mypage.FollowedPostsApi
import general.tag.TagSearchInput
import general.navigation.Router
import org.scalajs.dom
import models.*
import scala.concurrent.ExecutionContext.Implicits.global

object FollowedTagsView {
  // フォロー中タグのリスト
  private val tagsVar = Var(Seq.empty[FollowItemViewValue])
  private val isFetching = Var(false)
  private var tagsLoaded = false

  def apply(): HtmlElement = {
    div(
      cls := "w-full h-full min-h-0 flex flex-col bg-gray-50 dark:bg-gray-950 overflow-hidden",
      div(cls := "flex-none h-14 w-full"), // ヘッダースペーサー

      // --- 1. 検索 & 新規登録エリア ---
      div(
        cls := "flex-none bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 p-4 z-30 shadow-sm",
        span(cls := "block mb-1 text-[10px] font-black text-gray-400 uppercase tracking-widest", "Add New Tag"),
        // 修正したサジェスト対応の SearchBar を使用
        TagSearchInput(
          onSelect = item => followTag(item.id)
        )
      ),

      // --- 2. 件数サマリーエリア ---
      div(
        cls := "flex-none bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 px-5 py-3 flex items-center justify-between",
        span(cls := "text-[11px] font-black text-gray-400 uppercase tracking-widest", "Followed Tags"),
        child <-- tagsVar.signal.map { items =>
          span(
            cls := "text-sm font-black text-blue-600 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/30 px-4 py-1 rounded-full border border-blue-100 dark:border-blue-800 shadow-sm",
            s"${items.size} 件"
          )
        }
      ),

      // --- 3. 一覧エリア ---
      PullToRefresh(() => fetchTags())(
        div(
          cls := "p-4 pb-6",
          child <-- isFetching.signal.combineWith(tagsVar.signal).map { case (loading, items) =>
            if (loading && items.isEmpty) renderLoading()
            else if (items.isEmpty) renderEmptyState()
            else div(cls := "grid grid-cols-2 gap-3", items.map(renderTagItem))
          }
        )
      ),

      onMountCallback { _ =>
        if (!tagsLoaded) fetchTags()
      }
    )
  }

  // --- 各アイテムのレンダリング ---

  private def renderTagItem(item: FollowItemViewValue): HtmlElement = {
    val fId = item.followId
    div(
      // MyPageWordItem に合わせたスタイル
      cls := "bg-white dark:bg-gray-900 p-3 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 flex items-center justify-between group hover:border-blue-200 dark:hover:border-blue-700 transition-all",

      // タグ名：クリックで検索結果（タイムライン）へ
      span(
        cls := "text-sm font-bold text-gray-700 dark:text-gray-200 truncate cursor-pointer hover:text-blue-600 dark:hover:text-blue-400 hover:underline decoration-2 underline-offset-4",
        s"#${item.tagName}",
        onClick --> { _ => Router.router.pushState(SearchPage(item.tagName)) }
      ),

      // 解除ボタン（右上のマイナスアイコン風）
      button(
        tpe := "button",
        cls := "p-1.5 text-gray-300 dark:text-gray-600 hover:text-red-500 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-full transition-all active:scale-90",
        onClick --> { _ =>
          Modal.show(
            title = "フォロー解除",
            message = s"タグ「#${item.tagName}」のフォローを解除しますか？",
            ModalType.Confirm,
            unfollowTag(fId, item.tagId)
          )
        },
        s.svg(
          s.cls := "w-5 h-5",
          s.fill := "none",
          s.stroke := "currentColor",
          s.strokeWidth := "2.5",
          s.viewBox := "0 0 24 24",
          s.circle(s.cx := "12", s.cy := "12", s.r := "9"),
          s.path(s.d := "M9 12h6")
        )
      )
    )
  }

  private def renderLoading() = div(
    cls := "py-20 text-center",
    div(cls := "inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent")
  )

  private def renderEmptyState(): HtmlElement = {
    div(
      cls := "py-20 text-center flex flex-col items-center px-6",
      div(
        cls := "w-20 h-20 bg-white dark:bg-gray-900 rounded-full flex items-center justify-center shadow-sm mb-6",
        s.svg(
          s.cls := "w-12 h-12 text-gray-400",
          s.fill := "none",
          s.stroke := "currentColor",
          s.strokeWidth := "2",
          s.viewBox := "0 0 24 24",
          s.path(
            s.d := "M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z"
          )
        )
      ),
      h2(cls := "text-lg font-bold text-gray-900 dark:text-gray-100 mb-2", "フォロー中のタグはありません"),
      p(cls := "text-sm text-gray-400 leading-relaxed", "上の検索バーから\n興味のあるタグを追加しましょう。")
    )
  }

  // --- 通信ロジック ---

  private def fetchTags(): Unit = {
    isFetching.set(true)
    FollowedPostsApi.fetchTags().onComplete { res =>
      isFetching.set(false)
      tagsLoaded = true
      res.foreach(r => tagsVar.set(r.tagFollows))
    }
  }

  private def followTag(tagId: Long): Unit = {
    // ApiClient.postRaw[Map[...]] ではなく postRawIgnore を使う
    FollowedPostsApi.followTag(tagId).onComplete {
      case scala.util.Success(_) =>
        // 登録成功！リストを最新化する
        fetchTags()
      case scala.util.Failure(e) =>
        dom.console.error(s"Follow failed: ${e.getMessage}")
    }
  }

  private def unfollowTag(followId: Long, tagId: Long): Unit = {
    // DELETE /user/follows/15 のような形式でリクエスト
    FollowedPostsApi.unfollowTag(followId).onComplete {
      case scala.util.Success(_) =>
        // 成功したら画面上のリストから削除
        tagsVar.update(_.filterNot(_.tagId == tagId))
      case scala.util.Failure(e) =>
        dom.console.error(s"削除失敗: ${e.getMessage}")
    }
  }
}
