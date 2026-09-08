package controllers

import javax.inject.*
import play.api.mvc.*
import upickle.default.*
import models.AdminUserView
import repositories.UserRepository
import scala.concurrent.ExecutionContext
import actions.AdminAuthenticatedAction

@Singleton
class ManagerController @Inject() (
    val controllerComponents: ControllerComponents,
    adminAuthenticatedAction: AdminAuthenticatedAction,
    userRepository: UserRepository
)(implicit ec: ExecutionContext)
    extends BaseController {

  def index(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.manager())
  }

  def recentUsers(limit: Option[Int], offset: Option[Int]): Action[AnyContent] = adminAuthenticatedAction.async {
    implicit request =>
      userRepository
        .listRecent(utils.Validation.boundedLimit(limit, 100), utils.Validation.boundedOffset(offset))
        .map { users =>
          val response = users.map { user =>
            AdminUserView(
              userId = user.userId,
              displayId = user.displayId,
              userName = user.userName,
              roleId = user.roleId,
              userRegistrationDate = user.userRegistrationDate
            )
          }
          Ok(write(response)).as("application/json;charset=utf-8")
        }
  }
}
