package controllers

import javax.inject._
import play.api.mvc._
import upickle.default._
import services.NotificationService
import scala.concurrent.ExecutionContext
import play.api.libs.json.Json
import actions.AuthenticatedAction

@Singleton
class NotificationController @Inject() (
    val controllerComponents: ControllerComponents,
    authenticatedAction: AuthenticatedAction,
    notificationService: NotificationService
)(implicit ec: ExecutionContext)
    extends BaseController {

  def getNotifications: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val userId = request.userId
    notificationService.getNotificationsForUser(userId).map { response =>
      Ok(write(response)).as("application/json")
    }
  }

  def getNotificationStatus: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val userId = request.userId
    for {
      hasUnread <- notificationService.hasUnreadNotifications(userId)
      unreadCount <- notificationService.getUnreadCount(userId)
    } yield {
      Ok(
        Json.obj(
          "hasUnread" -> hasUnread,
          "unreadCount" -> unreadCount
        )
      )
    }
  }

  def markAsRead(notificationId: Long): Action[AnyContent] = authenticatedAction.async { implicit request =>
    notificationService.markAsRead(notificationId, request.userId).map { _ =>
      Ok(Json.obj("success" -> true))
    }
  }

  def markAllAsRead: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val userId = request.userId
    notificationService.markAllAsRead(userId).map { count =>
      Ok(Json.obj("success" -> true, "count" -> count))
    }
  }
}
