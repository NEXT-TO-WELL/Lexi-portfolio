package general.ui
import general.ui.PullToRefresh
import general.auth.AuthStorage
import component.{Modal, ModalType}

import com.raquo.laminar.api.L._
import org.scalajs.dom
import general.navigation.TabStateManager
import general.notification.NotificationState
import models.HomePage

object Header {
  def apply(): HtmlElement = {
    headerTag(
      cls := "absolute top-0 left-0 w-full h-14 z-50 bg-white/80 dark:bg-gray-900/80 backdrop-blur-md border-b border-gray-100 dark:border-gray-800",
      cls := "pt-[env(safe-area-inset-top)]",

      div(
        // grid を使って 3カラム(1:auto:1) にするのが中央配置には最も確実です
        cls := "relative grid grid-cols-3 items-center h-full px-4",

        // --- 左側：戻るボタン ---
        div(
          cls := "flex justify-start", // 左寄せ
          child <-- TabStateManager.currentStackSignal.map(_.size > 1).map {
            case true =>
              button(
                cls := "p-2 -ml-2 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-full transition",
                onClick --> { _ =>
                  if (dom.window.location.hash.contains("reply")) {
                    dom.window.history.back()
                  } else {
                    TabStateManager.popPage()
                  }
                },
                svg.svg(
                  svg.cls := "w-6 h-6",
                  svg.fill := "none",
                  svg.stroke := "currentColor",
                  svg.viewBox := "0 0 24 24",
                  svg.path(svg.strokeWidth := "2", svg.d := "M15 19l-7-7 7-7")
                )
              )
            case false => emptyNode
          }
        ),

        // --- 中央：タイトル (ここが常に真ん中にくる) ---
        div(
          cls := "flex items-center justify-center",
          h1(
            cls := "flex items-center justify-center",
            a(
              idAttr := "header-home-link",
              href := "/home",
              cls := "rounded-sm transition-opacity hover:opacity-75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600",
              onClick.preventDefault --> { _ => TabStateManager.resetStack(HomePage) },
              img(
                idAttr := "header-logotype",
                src := "/assets/images/lexi-logotype.svg",
                cls := "block h-8 w-auto max-w-[min(52vw,12rem)]",
                alt := "Lexi"
              )
            )
          )
        ),

        // --- 右側：アクションエリア ---
        div(
          cls := "flex justify-end items-center space-x-1", // 右寄せ
          child <-- PullToRefresh.DesktopRefresh.actionVar.signal.map {
            case Some(_) =>
              button(
                idAttr := "desktop-refresh-button",
                aria.label := "ページを更新",
                cls <-- PullToRefresh.DesktopRefresh.refreshingVar.signal.map { refreshing =>
                  val toneCls =
                    if (refreshing) "text-blue-600 dark:text-blue-400 cursor-wait bg-blue-50 dark:bg-blue-900/30"
                    else
                      "text-gray-500 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-gray-100 dark:hover:bg-gray-800 active:scale-90"
                  s"relative hidden p-2 rounded-full transition-all md:inline-flex $toneCls"
                },
                disabled <-- PullToRefresh.DesktopRefresh.refreshingVar.signal,
                title := "更新",
                onClick --> { _ => PullToRefresh.DesktopRefresh.run() },
                svg.svg(
                  svg.cls <-- PullToRefresh.DesktopRefresh.refreshingVar.signal
                    .map(if (_) "w-5 h-5 animate-spin" else "w-5 h-5"),
                  svg.fill := "none",
                  svg.stroke := "currentColor",
                  svg.strokeWidth := "2.25",
                  svg.viewBox := "0 0 24 24",
                  svg.path(
                    svg.strokeLineCap := "round",
                    svg.strokeLineJoin := "round",
                    svg.d := "M21 12a9 9 0 11-2.64-6.36"
                  ),
                  svg.path(svg.strokeLineCap := "round", svg.strokeLineJoin := "round", svg.d := "M21 3v6h-6")
                ),
                child <-- PullToRefresh.DesktopRefresh.refreshingVar.signal.map { refreshing =>
                  if (refreshing) span(cls := "absolute top-1 right-1 w-1 h-1 rounded-full bg-blue-500")
                  else emptyNode
                }
              )
            case None => emptyNode
          },
          if (AuthStorage.getToken.isDefined) {
            Seq(
              // 通知ボタン
              button(
                tpe := "button",
                aria.label := "お知らせ",
                cls := "p-2 rounded-full transition relative text-gray-600 dark:text-gray-400",
                onClick --> { _ => NotificationState.isShowingVar.update(!_) },
                svg.svg(
                  svg.cls := "w-6 h-6",
                  svg.fill <-- NotificationState.hasUnreadVar.signal.map(if (_) "currentColor" else "none"),
                  svg.stroke := "currentColor",
                  svg.viewBox := "0 0 24 24",
                  svg.path(
                    svg.strokeWidth := "2",
                    svg.d := "M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
                  )
                ),
                child <-- NotificationState.unreadCountVar.signal.map { count =>
                  if (count > 0) {
                    span(
                      idAttr := "notification-unread-count",
                      cls := "absolute top-1 right-1 flex items-center justify-center min-w-[16px] h-[16px] px-1 text-[9px] font-bold text-white bg-red-500 rounded-full",
                      if (count > 99) "99+" else count.toString
                    )
                  } else emptyNode
                }
              ),
              // ログアウトボタン
              button(
                cls := "p-2 -mr-2 text-gray-400 hover:text-red-500 rounded-full transition-all active:scale-90",
                onClick --> { _ =>
                  Modal.show("ログアウト", "ログアウトしてもよろしいですか？", ModalType.Confirm, { AuthStorage.logout() })
                },
                svg.svg(
                  svg.cls := "w-6 h-6",
                  svg.fill := "none",
                  svg.stroke := "currentColor",
                  svg.strokeWidth := "2",
                  svg.viewBox := "0 0 24 24",
                  svg.path(
                    svg.d := "M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
                  )
                )
              )
            )
          } else {
            // ゲスト時は空の div が grid の 1枠を占めるため、中央が維持される
            emptyNode
          }
        )
      )
    )
  }
}
