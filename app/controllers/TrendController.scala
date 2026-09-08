package controllers

import javax.inject._
import play.api.mvc._
import upickle.default._
import scala.concurrent.ExecutionContext

import models._
import services._
import actions.AdminAuthenticatedAction

@Singleton
class TrendController @Inject() (
    val controllerComponents: ControllerComponents,
    trendService: TrendService,
    adminAuthenticatedAction: AdminAuthenticatedAction
)(implicit ec: ExecutionContext)
    extends BaseController {

  def getTrends: Action[AnyContent] = Action.async { implicit request =>
    trendService.getFullTrends.map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def updateTrends: Action[AnyContent] = adminAuthenticatedAction.async {
    trendService.updateAllTrends().map { _ =>
      // MapをJSON文字列に変換して返す
      val response = Map(
        "status" -> "success",
        "message" -> "Trends updated"
      )
      Ok(write(response)).as("application/json")
    }
  }
}
