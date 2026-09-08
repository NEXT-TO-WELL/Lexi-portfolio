package general.notification

import com.raquo.laminar.api.L.Var
import scala.concurrent.ExecutionContext.Implicits.global
import util.ApiClient

object NotificationState {
  val isShowingVar = Var(false)
  val hasUnreadVar = Var(false)
  val unreadCountVar = Var(0)

  def markAllReadLocally(): Unit = {
    hasUnreadVar.set(false)
    unreadCountVar.set(0)
  }

  def refreshUnreadStatus(): Unit = {
    ApiClient.get[ujson.Value]("/notifications/status").foreach { response =>
      hasUnreadVar.set(response.obj.get("hasUnread").flatMap(_.boolOpt).getOrElse(false))
      unreadCountVar.set(response.obj.get("unreadCount").flatMap(_.numOpt).map(_.toInt).getOrElse(0))
    }
  }
}
