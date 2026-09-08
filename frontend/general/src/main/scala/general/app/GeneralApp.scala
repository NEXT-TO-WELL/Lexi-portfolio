package general.app

import general.auth.AuthStorage
import component.Modal
import general.ui.ReportModal
import general.ui.EditModal
import general.ui.Header
import general.notification.NotificationModal
import general.ui.Toast

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import general.navigation.{Router, Tab}
import general.notification.NotificationState
import general.notification.PushNotificationClient
import models.HomePage

object GeneralApp {
  def appElement(): HtmlElement = {
    div(
      GlobalStyles.element,
      cls := "min-h-screen bg-gray-200 dark:bg-gray-950 flex justify-center transition-colors",

      onClick --> { ev =>
        val target = ev.target.asInstanceOf[dom.html.Element]

        // 入力要素、ボタン、リンク、サジェストリスト（またはその中身）を触っている時は無視する
        // これをしないと、ボタンを押した瞬間にフォーカスが外れてクリックイベントが消えることがあります
        val isInteractive = List("INPUT", "TEXTAREA", "BUTTON", "A", "SVG", "PATH")
          .contains(target.tagName.toUpperCase) || target.closest(".cursor-pointer") != null

        if (!isInteractive) {
          dom.document.activeElement match {
            case el: dom.html.Input    => el.blur()
            case el: dom.html.TextArea => el.blur()
            case _                     => ()
          }
        }
      },

      div(
        // ここが実際の「スマホ画面」の枠組み
        // w-full (スマホ) / max-w-3xl (PC: 768px) / JavaScriptで動的に高さ調整
        cls := "w-full max-w-3xl h-[100dvh] bg-white dark:bg-gray-900 relative flex flex-col shadow-2xl overflow-hidden",
//        styleAttr := s"height: ${dom.window.innerHeight}px",
        onMountCallback { _ =>
          if (AuthStorage.getToken.isDefined) {
            NotificationState.refreshUnreadStatus()
          }
        },
        // ヘッダー
        Header(),
        // メインコンテンツエリア
        // TabRouter.router.currentPageSignal を map して表示を切り替える
        mainTag(
          idAttr := "app-content",
          cls <-- Router.router.currentPageSignal.map(PageContent.cssClass),
          child <-- Router.router.currentPageSignal.map(PageContent.render)
        ),
        // The navigation remains fixed, while this spacer reserves its full layout height.
        div(
          idAttr := "bottom-navigation-clearance",
          aria.hidden := true,
          cls := "shrink-0 h-[calc(4rem+env(safe-area-inset-bottom))]"
        ),
        Modal(),
        EditModal(),
        ReportModal(),
        Toast.render(),
        child <-- NotificationState.isShowingVar.signal.map(if (_) NotificationModal() else emptyNode),

        // ボトムナビゲーションバー
        Tab()
      )
    )
  }

  private val appContainer = dom.document.getElementById("app")

  def main(args: Array[String]): Unit = {
    render(appContainer, appElement())

    if (AuthStorage.getToken.isDefined) {
      PushNotificationClient.markLaunchNotificationRead()
      PushNotificationClient.installForegroundListener(() => NotificationState.refreshUnreadStatus())
    }

    if (dom.window.location.pathname == "/" || dom.window.location.pathname == "") {
      Router.router.replaceState(HomePage)
    }
  }
}
