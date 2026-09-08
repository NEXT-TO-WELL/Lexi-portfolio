package general.auth

import org.scalajs.dom
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

object AuthStorage {
  private given ExecutionContext = ExecutionContext.global

  private val TokenKey = "auth_token"
  private val UserIdKey = "auth_user_id"
  private val RoleIdKey = "auth_role_id"

  def saveAuth(token: String, userId: String, roleId: String): Unit = {
    dom.window.localStorage.setItem(TokenKey, token)
    dom.window.localStorage.setItem(UserIdKey, userId)
    dom.window.localStorage.setItem(RoleIdKey, roleId)
  }

  def getToken: Option[String] = Option(dom.window.localStorage.getItem(TokenKey))

  def getUserId: Option[String] =
    Option(dom.window.localStorage.getItem(UserIdKey))

  def getRoleId: Option[String] = {
    Option(dom.window.localStorage.getItem(RoleIdKey))
  }

  def isAdmin: Boolean = getRoleId.contains("admin")

  def isModerator: Boolean = {
    val role = getRoleId.getOrElse("")
    role == "admin" || role == "moderator"
  }

  def logout(): Unit = {
    val request = getToken match {
      case Some(token) =>
        val csrfToken = Option(dom.document.querySelector("meta[name='csrf-token']"))
          .map(_.getAttribute("content"))
          .getOrElse("")
        val endpoint = if (isAdmin) "/api/admin/logout" else "/api/logout"
        dom.window
          .fetch(
            endpoint,
            new dom.RequestInit {
              method = dom.HttpMethod.POST
              headers = js.Dictionary("Authorization" -> s"Bearer $token", "Csrf-Token" -> csrfToken)
            }
          )
          .toFuture
          .map(_ => ())
          .recover { case _ => () }
      case None => Future.successful(())
    }

    request.foreach(_ => finishLogout())
  }

  private def finishLogout(): Unit = {
    clearAuth()
    dom.window.location.href = "/signin"
  }

  def clearAuth(): Unit = {
    dom.window.localStorage.removeItem(TokenKey)
    dom.window.localStorage.removeItem(UserIdKey)
    dom.window.localStorage.removeItem(RoleIdKey)
  }
}
