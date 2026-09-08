package component

import com.raquo.laminar.api.L.*
import models.AdminUserView
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global
import util.ApiClient

object UserList {
  private val usersVar = Var(Seq.empty[AdminUserView])
  private val isLoading = Var(true)

  def apply(): HtmlElement = {
    div(
      cls := "space-y-4",
      div(
        cls := "flex justify-between items-center",
        h2(cls := "text-xl font-bold text-gray-900", "新規ユーザー"),
        button(
          cls := "px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition",
          "再読み込み",
          onClick --> { _ => fetchUsers() }
        )
      ),
      child <-- isLoading.signal.map { loading =>
        if (loading) {
          div(
            cls := "py-20 text-center",
            div(cls := "inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent")
          )
        } else emptyNode
      },
      child <-- usersVar.signal.combineWith(isLoading.signal).map { case (users, loading) =>
        if (loading) emptyNode
        else if (users.isEmpty) {
          div(cls := "bg-white rounded-lg shadow p-8 text-center text-gray-500", "ユーザーはありません")
        } else {
          div(
            cls := "bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden",
            table(
              cls := "w-full text-sm",
              thead(
                cls := "bg-gray-50 text-gray-500",
                tr(
                  th(cls := "px-4 py-3 text-left font-bold", "表示名"),
                  th(cls := "px-4 py-3 text-left font-bold", "ユーザーID"),
                  th(cls := "px-4 py-3 text-left font-bold", "内部ID"),
                  th(cls := "px-4 py-3 text-left font-bold", "権限"),
                  th(cls := "px-4 py-3 text-left font-bold", "登録日時")
                )
              ),
              tbody(
                users.map(renderUserRow)
              )
            )
          )
        }
      },
      onMountCallback { _ => fetchUsers() }
    )
  }

  private def renderUserRow(user: AdminUserView): HtmlElement = {
    val dateStr = user.userRegistrationDate
      .map(ms => new scala.scalajs.js.Date(ms.toDouble).toLocaleString())
      .getOrElse("-")

    tr(
      cls := "border-t border-gray-100 hover:bg-gray-50",
      td(cls := "px-4 py-3 font-bold text-gray-900", user.userName),
      td(cls := "px-4 py-3 text-gray-700", s"@${user.displayId}"),
      td(cls := "px-4 py-3 text-gray-500 font-mono text-xs", user.userId),
      td(
        cls := "px-4 py-3",
        span(
          cls := "px-2 py-1 rounded text-xs font-bold bg-gray-100 text-gray-700",
          user.roleId
        )
      ),
      td(cls := "px-4 py-3 text-gray-500", dateStr)
    )
  }

  private def fetchUsers(): Unit = {
    isLoading.set(true)
    val future = ApiClient.get[Seq[AdminUserView]]("/manager/users")
    future.foreach { users =>
      usersVar.set(users)
      isLoading.set(false)
    }
    future.recover { case ex =>
      dom.console.error(s"Failed to fetch users: ${ex.getMessage}")
      isLoading.set(false)
    }
  }
}
