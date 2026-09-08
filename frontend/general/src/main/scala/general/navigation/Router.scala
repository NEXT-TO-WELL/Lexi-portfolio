package general.navigation

import com.raquo.laminar.api.L.*
import com.raquo.waypoint.*
import general.notification.NotificationState
import org.scalajs.dom
import upickle.default.*
import models._

object Router {
  // URLパターンとページ型のマッピング
  private val routes = List(
    Route.static(HomePage, root / "home" / endOfSegments),
    Route.static(PostPage, root / "post" / endOfSegments),
    Route.static(MyPostsPage, root / "mypage" / "posts" / endOfSegments),
    Route.static(FavoritePostsPage, root / "mypage" / "favorites" / endOfSegments),
    Route.static(ReactedPostsPage, root / "mypage" / "reactions" / endOfSegments),
    Route.static(FollowedTagsPage, root / "mypage" / "followedTags" / endOfSegments),
    Route.static(FollowedWordsPage, root / "mypage" / "followedWords" / endOfSegments),
    Route.static(HistoryPage, root / "mypage" / "history" / endOfSegments),
    Route.static(MyPage, root / "mypage" / endOfSegments),
    Route.onlyQuery[SearchPage, (Option[String], Option[Long], Option[Long])](
      encode = page =>
        (
          if (page.query.nonEmpty) Some(page.query) else None,
          page.wordId,
          page.tagId
        ),
      decode = { case (q, wId, tId) =>
        SearchPage(query = q.getOrElse(""), wordId = wId, tagId = tId)
      },
      // 名前付き引数を使わず、第3引数としてパターンを渡す
      // root / "search" はパス、その後に ? でクエリを結合
      (root / "search" / endOfSegments) ? (
        com.raquo.waypoint.param[String]("q").? &
          com.raquo.waypoint.param[Long]("wordId").? &
          com.raquo.waypoint.param[Long]("tagId").?
      )
    ),
    // Router.scala 内の Route定義を修正
    Route[WordDetailPage, (Long, Long, String)](
      // Page型からURL (wordId, tagId, tabName) へ
      encode = p => (p.wordId, p.tagId, p.initialTab.toString.toLowerCase),

      // URLからPage型へ (tabName を WordDetailTab 型に戻す)
      decode = { case (wId, tId, tabName) =>
        val tab = tabName match {
          case "example" => WordDetailTab.Example
          case "thread"  => WordDetailTab.Thread
          case _         => WordDetailTab.Meaning
        }
        WordDetailPage(wId, tId, tab)
      },
      // パターンを /word/1/tag/10/thread のように拡張
      pattern = root / "word" / segment[Long] / "tag" / segment[Long] / segment[String]
    ),
    Route[ThreadDetailPage, Long](
      encode = p => p.threadId,
      decode = id => ThreadDetailPage(id),
      pattern = root / "threads" / segment[Long]
    )
  )

  // ルーター本体の作成
  val router = new Router[Page](
    routes = routes,
    serializePage = page => write(page),
    deserializePage = json => read[Page](json),
    getPageTitle = _ => "Lexi SNS",
    routeFallback = _ => HomePage, // 存在しないURLにアクセスした時のリダイレクト先
    popStateEvents = windowEvents(_.onPopState),
    owner = unsafeWindowOwner
  )

  // ★ 追加: ページ遷移を監視し、タブごとの履歴を常に更新する
  // これにより、WordDetailPageに飛んだ瞬間に「ホームタブの履歴」がWordDetailPageになります
  router.currentPageSignal.foreach { page =>
    // ページ遷移時に通知モーダルを閉じる
    if (NotificationState.isShowingVar.now()) {
      NotificationState.isShowingVar.set(false)
    }
    TabStateManager.pushPage(page)
  }(unsafeWindowOwner)

  // 遷移用のヘルパー関数
  def navigateTo(page: Page): Unit = {
    router.pushState(page)
    dom.window.requestAnimationFrame { _ =>
      dom.window.scrollTo(0, 0)
    }
  }
}
