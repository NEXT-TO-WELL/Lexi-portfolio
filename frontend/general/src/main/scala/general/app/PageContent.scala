package general.app

import com.raquo.laminar.api.L.HtmlElement
import models.*
import viewPages.*

object PageContent {
  def cssClass(page: Page): String = page match {
    case MyPostsPage | _: WordDetailPage | _: ThreadDetailPage | FavoritePostsPage | ReactedPostsPage |
        FollowedTagsPage | FollowedWordsPage | HistoryPage =>
      "flex-1 min-h-0 overflow-hidden px-4"
    case HomePage =>
      "flex-1 min-h-0 overflow-hidden"
    case _ =>
      "flex-1 min-h-0 pt-14 px-4 overflow-y-auto"
  }

  def render(page: Page): HtmlElement = page match {
    case HomePage                     => HomeView()
    case PostPage                     => PostView()
    case MyPage                       => MypageView()
    case MyPostsPage                  => MyPostsView()
    case FavoritePostsPage            => FavoritePostsView()
    case ReactedPostsPage             => ReactedPostsView()
    case FollowedTagsPage             => FollowedTagsView()
    case FollowedWordsPage            => FollowedWordsView()
    case HistoryPage                  => HistoryView()
    case searchPage: SearchPage       => SearchView(searchPage)
    case wordPage: WordDetailPage     => WordDetailView(wordPage)
    case threadPage: ThreadDetailPage => ThreadDetailView(threadPage)
  }
}
