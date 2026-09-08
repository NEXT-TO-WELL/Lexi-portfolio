package general.mypage

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import general.auth.AuthStorage
import general.notification.PushNotificationClient
import general.navigation.Router
import models.*
import scala.concurrent.ExecutionContext.Implicits.global

object MypageProfileContent {
  final case class SettingsActions(
      editName: String => Unit,
      showPassword: () => Unit,
      showDeleteAccount: () => Unit
  )

  def apply(profile: MyProfileResponse, settings: SettingsActions): HtmlElement =
    div(
      cls := "w-full",
      profileHeader(profile),
      statistics(profile),
      navigationMenu(),
      settingsMenu(profile.userName, settings),
      logoutSection()
    )

  private def profileHeader(profile: MyProfileResponse): HtmlElement =
    div(
      cls := "bg-white dark:bg-gray-900 px-4 pt-6 pb-4 border-b border-gray-100 dark:border-gray-700",
      div(
        cls := "flex items-center space-x-4",
        div(
          cls := "w-16 h-16 bg-blue-600 dark:bg-blue-500 rounded-full flex items-center justify-center text-white font-bold text-xl shadow-inner",
          profile.userName.take(1)
        ),
        div(
          h2(cls := "text-xl font-bold text-gray-900 dark:text-gray-100", profile.userName),
          div(
            cls := "mt-1 flex flex-col gap-0.5",
            span(cls := "text-sm text-gray-500 dark:text-gray-400", s"@${profile.displayId}"),
            span(cls := "text-[11px] font-medium text-gray-400 dark:text-gray-500", s"ユーザーID: ${profile.userId}")
          )
        )
      )
    )

  private def statistics(profile: MyProfileResponse): HtmlElement =
    div(
      cls := "grid grid-cols-4 gap-px bg-gray-100 dark:bg-gray-800 border-b border-gray-100 dark:border-gray-700",
      stat("語釈・用例", profile.totalPosts),
      stat("スレッド", profile.totalThreads),
      stat("コメント", profile.totalComments),
      stat("お気に入り", profile.totalFavorites)
    )

  private def navigationMenu(): HtmlElement =
    menuSection(
      menuItem(
        "自分の投稿",
        "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253",
        () => Router.router.pushState(MyPostsPage)
      ),
      menuItem(
        "お気に入り投稿",
        "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z",
        () => Router.router.pushState(FavoritePostsPage)
      ),
      menuItem(
        "リアクションした投稿",
        "M14 9V5a3 3 0 00-6 0v4m-3 0h12l-1 10H6L5 9zm4 4h.01M15 13h.01",
        () => Router.router.pushState(ReactedPostsPage)
      ),
      menuItem(
        "フォロー中のタグ",
        "M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z",
        () => Router.router.pushState(FollowedTagsPage)
      ),
      menuItem(
        "フォロー中の言葉",
        "M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z",
        () => Router.router.pushState(FollowedWordsPage)
      ),
      menuItem("閲覧履歴", "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z", () => Router.router.pushState(HistoryPage))
    )

  private def settingsMenu(userName: String, actions: SettingsActions): HtmlElement =
    menuSection(
      menuItem(
        "表示名を変更",
        "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z",
        () => actions.editName(userName)
      ),
      menuItem(
        "パスワードを変更",
        "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z",
        actions.showPassword
      ),
      menuItem(
        "アカウントを削除",
        "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16",
        actions.showDeleteAccount
      )
    )

  private def stat(label: String, value: Int): HtmlElement =
    div(
      cls := "bg-white dark:bg-gray-900 py-4 flex flex-col items-center justify-center",
      span(cls := "text-lg font-black text-gray-900 dark:text-gray-100", value.toString),
      span(cls := "text-[10px] text-gray-400 dark:text-gray-500 font-medium", label)
    )

  private def menuSection(items: HtmlElement*): HtmlElement =
    div(
      cls := "mt-4 bg-white dark:bg-gray-900 border-y border-gray-100 dark:border-gray-700 divide-y divide-gray-50 dark:divide-gray-800",
      items
    )

  private def menuItem(label: String, iconPath: String, action: () => Unit): HtmlElement =
    button(
      cls := "w-full flex items-center justify-between px-4 py-4 hover:bg-gray-50 dark:hover:bg-gray-800 active:bg-gray-100 dark:active:bg-gray-700 transition",
      onClick --> (_ => action()),
      div(
        cls := "flex items-center space-x-3",
        svg.svg(
          svg.cls := "w-5 h-5 text-gray-400 dark:text-gray-500",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.viewBox := "0 0 24 24",
          svg.path(svg.d := iconPath)
        ),
        span(cls := "text-sm font-bold text-gray-700 dark:text-gray-300", label)
      ),
      svg.svg(
        svg.cls := "w-4 h-4 text-gray-300 dark:text-gray-600",
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 24 24",
        svg.path(svg.d := "M9 5l7 7-7 7")
      )
    )

  private def logoutSection(): HtmlElement =
    div(
      cls := "mt-8 px-4 pb-10",
      button(
        tpe := "button",
        cls := "w-full flex items-center justify-center space-x-2 py-3 border border-red-200 dark:border-red-800 rounded-xl text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 hover:bg-red-100 dark:hover:bg-red-900/30 active:bg-red-200 dark:active:bg-red-900/40 transition",
        onClick --> { _ =>
          Modal.show(
            "ログアウト",
            "ログアウトしてもよろしいですか？",
            ModalType.Confirm, {
              PushNotificationClient.disableForLogout().foreach(_ => AuthStorage.logout())
            }
          )
        },
        svg.svg(
          svg.cls := "w-5 h-5",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.viewBox := "0 0 24 24",
          svg.path(svg.d := "M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1")
        ),
        span(cls := "font-bold text-sm", "ログアウト")
      ),
      p(cls := "mt-4 text-center text-[10px] text-gray-400 dark:text-gray-500", "Lexi v1.0.0-SNAPSHOT")
    )
}
