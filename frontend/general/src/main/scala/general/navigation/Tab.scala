package general.navigation

import com.raquo.laminar.api.L._
import general.auth.AuthStorage
import models._
import viewPages._

object Tab {
  case class NavItem(label: String, iconPath: String, rootPage: Page)

  def apply(): HtmlElement = {
    val items = List(
      NavItem(
        "ホーム",
        "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6",
        HomePage
      ),
      NavItem(
        "投稿",
        "M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5M18.364 2.636a2 2 0 012.828 2.828l-9.9 9.9-4.243 1.414 1.414-4.243 9.9-9.9z",
        PostPage
      ),
      NavItem("マイページ", "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z", MyPage)
    )

    navTag(
      idAttr := "bottom-navigation",
      aria.label := "主要ナビゲーション",
      cls := "fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-3xl min-h-[calc(4rem+env(safe-area-inset-bottom))] bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-t border-gray-100 dark:border-gray-800 z-50 antialiased pb-[env(safe-area-inset-bottom)]",
      div(
        cls := "flex justify-around items-center h-16",

        items.map { item =>
          val isGuest = AuthStorage.getToken.isEmpty
          val isPostButton = item.label == "投稿"
          val tabId = item.label match {
            case "投稿"    => "post"
            case "マイページ" => "my"
            case _       => "home"
          }
          val tabStateSignal = TabStateManager.activeTabId.signal.map { activeId =>
            val isActive = activeId == tabId
            if (isPostButton) {
              if (isGuest) {
                "border-gray-200 bg-gray-100 text-gray-400 opacity-60 cursor-not-allowed dark:border-gray-700 dark:bg-gray-800 dark:text-gray-500"
              } else if (isActive) {
                "border-[#17324d] bg-[#17324d] text-white shadow-sm dark:border-[#e7edf3] dark:bg-[#e7edf3] dark:text-[#17324d]"
              } else {
                "border-[#e6b08d] bg-[#f8e9df] text-[#a84f2f] hover:border-[#d88b5d] hover:bg-[#f4ddcf] dark:border-[#79503d] dark:bg-[#3f302b] dark:text-[#f1b28d] dark:hover:bg-[#4a362f]"
              }
            } else if (isActive) {
              "text-blue-600 dark:text-blue-400"
            } else {
              "text-gray-400 dark:text-gray-500"
            }
          }

          button(
            dataAttr("tab-id") := tabId,
            dataAttr("action-emphasis") := isPostButton.toString,
            dataAttr("tab-state") <-- TabStateManager.activeTabId.signal.map { activeId =>
              if (isGuest && isPostButton) "disabled"
              else if (activeId == tabId) "active"
              else "default"
            },
            cls := (if (isPostButton)
                      "flex-1 h-12 mx-2 rounded-xl border flex flex-col items-center justify-center group transition-[background-color,border-color,color,transform,box-shadow] duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#d97745] focus-visible:ring-inset"
                    else
                      "flex-1 h-full flex flex-col items-center justify-center group transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-inset"),
            typ := "button",

            // ゲストかつ投稿ボタンの場合、見た目と挙動を変更
            if (isGuest && isPostButton) title := "ログインすると投稿できます" else emptyNode,

            // 選択中の色判定
            cls <-- tabStateSignal,

            onClick.preventDefault --> { _ =>
              if (!(isGuest && isPostButton)) { // ゲストの投稿ボタンクリックは無視
                // 各タブのスタックの先頭（現在のページ）を取得
                val targetPage = TabStateManager.currentStackSignalFor(tabId).now().head

                val currentPage = Router.router.currentPageSignal.now()

                if (currentPage == targetPage) {
                  // すでにそのタブの現在のページにいる場合（二度押し）
                  if (currentPage != item.rootPage) {
                    // スタックをルートまで一気に戻す
                    TabStateManager.resetStack(item.rootPage)
                  } else if (item.rootPage == HomePage) {
                    HomeView.refresh() // ホームかつルートなら更新
                  }
                } else {
                  // 違うタブ、あるいは同じタブでも違う階層にいるなら履歴の先頭を表示
                  Router.navigateTo(targetPage)
                }
              }
            },

            div(
              cls := (if (isPostButton)
                        "relative p-0.5 group-active:scale-90 transition-transform"
                      else "relative p-1 group-active:scale-90 transition-transform"),
              svg.svg(
                svg.cls := (if (isPostButton) "w-6 h-6" else "w-7 h-7"),
                svg.fill := "none",
                svg.stroke := "currentColor",
                svg.strokeWidth := "2",
                svg.viewBox := "0 0 24 24",
                svg.path(
                  svg.strokeLineCap := "round",
                  svg.strokeLineJoin := "round",
                  svg.strokeWidth := (if (isPostButton) "1.9" else "1.5"),
                  svg.d := item.iconPath
                )
              )
            ),
            span(
              cls := (if (isPostButton) "text-[11px] font-bold leading-none" else "text-[10px] font-medium"),
              item.label
            )
          )
        }
      )
    )
  }
}
