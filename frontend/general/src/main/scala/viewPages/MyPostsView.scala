package viewPages
import general.ui.PullToRefresh

import com.raquo.laminar.api.L.*
import general.mypage.{MyPostsActions, MyPostsLists, MyPostsState, MyPostsTab}
import general.mypage.MyPostsTab.*

object MyPostsView {
  private val state = new MyPostsState
  private val actions = new MyPostsActions(state, () => MypageView.refreshProfile())
  import state.*

  def apply(): HtmlElement = {
    div(
      // 共通シェルがヘッダーとボトムナビを除いた利用可能領域を管理する。
      cls := "w-full h-full min-h-0 flex flex-col bg-gray-50 dark:bg-gray-950 overflow-hidden",

      // 2. 透明なスペーサー（MobileのHeader pt-14 分）
      div(cls := "flex-none h-14"),

      // 3. 固定ヘッダー（タブ部分）
      div(
        cls := "flex-none bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 z-20 shadow-sm",
        div(
          cls := "h-0.5 bg-blue-500 transition-all duration-300",
          width <-- isFetchingVar.signal.map(if (_) "100%" else "0%"),
          opacity <-- isFetchingVar.signal.map(if (_) "1" else "0")
        ),
        div(
          cls := "flex overflow-x-auto no-scrollbar",
          renderTabButton("言葉", Words),
          renderTabButton("語釈", Meanings),
          renderTabButton("用例", Examples),
          renderTabButton("スレ", Threads),
          renderTabButton("コメ", Comments)
        )
      ),

      // 4. コンテンツエリア
      PullToRefresh(() => actions.refreshCurrentTab())(
        div(
          cls := "p-4 pb-6",
          child <-- MyPostsLists(state, actions)
        )
      ),

      onMountCallback { _ =>
        if (!state.isLoaded(Words)) actions.fetch(Words)
      }
    )
  }

  private def renderTabButton(label: String, tab: MyPostsTab): HtmlElement = {
    button(
      cls <-- activeTabVar.signal.map { t =>
        val activeCls =
          if (t == tab) "border-blue-600 text-blue-600 dark:text-blue-400"
          else "border-transparent text-gray-400 dark:text-gray-500"
        s"flex-1 py-4 text-sm font-bold border-b-2 transition-all whitespace-nowrap $activeCls"
      },
      opacity <-- isFetchingVar.signal.map(if (_) 0.5 else 1.0),
      onClick --> (_ => actions.selectTab(tab)),
      label
    )
  }

}
