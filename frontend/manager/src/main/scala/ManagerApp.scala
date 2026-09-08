import com.raquo.laminar.api.L.*
import org.scalajs.dom
import component.{ReportList, UserList}
import general.auth.AuthStorage

object ManagerApp {
  def main(args: Array[String]): Unit = {
    // 認証チェック：トークンがない、または管理者でない場合はログイン画面にリダイレクト
    if (AuthStorage.getToken.isEmpty) {
      dom.window.location.href = "/signIn"
      return
    }

    if (!AuthStorage.isAdmin) {
      dom.window.location.href = "/home"
      return
    }

    val appContainer = dom.document.getElementById("manager-app")
    render(appContainer, appElement())
  }

  def appElement(): HtmlElement = {
    val activeTab = Var("reports")

    div(
      cls := "min-h-screen bg-gray-50",

      // ヘッダー
      div(
        cls := "bg-white shadow-sm border-b",
        div(
          cls := "max-w-7xl mx-auto px-4 py-4",
          h1(
            cls := "text-2xl font-bold text-gray-900",
            "Lexi Manager"
          )
        )
      ),

      // メインコンテンツ
      div(
        cls := "max-w-7xl mx-auto px-4 py-8",
        div(
          cls := "mb-6 border-b border-gray-200",
          div(
            cls := "flex gap-1",
            managerTabButton("reports", "報告", activeTab),
            managerTabButton("users", "新規ユーザー", activeTab)
          )
        ),
        child <-- activeTab.signal.map {
          case "users" => UserList()
          case _       => ReportList()
        }
      )
    )
  }

  private def managerTabButton(tabId: String, label: String, activeTab: Var[String]): HtmlElement = {
    button(
      cls := "px-4 py-3 text-sm font-bold border-b-2 transition",
      cls <-- activeTab.signal.map { active =>
        if (active == tabId) "border-blue-600 text-blue-600"
        else "border-transparent text-gray-500 hover:text-gray-800"
      },
      label,
      onClick --> { _ => activeTab.set(tabId) }
    )
  }
}
