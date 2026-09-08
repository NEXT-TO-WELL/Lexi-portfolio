package controllers

import javax.inject.*
import play.api.libs.json.*
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import services.ReactionService
import play.api.mvc.*
import actions.AuthenticatedAction
import shared.api.{ErrorResponse, ReactionToggleRequest}

@Singleton
class ReactionController @Inject() (
    val controllerComponents: ControllerComponents,
    reactionService: ReactionService,
    authenticatedAction: AuthenticatedAction
)(implicit ec: ExecutionContext)
    extends BaseController {

  def toggle: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val bodyText = Json.stringify(request.body)

    scala.util.Try(read[ReactionToggleRequest](bodyText)) match {
      case scala.util.Success(req) =>
        // トークンから取得した信頼できる userId を使用
        val userId = request.userId

        reactionService
          .toggleReaction(req, userId)
          .map { response =>
            Ok(write(response)).as("application/json;charset=utf-8")
          }
          .recover { case e: Exception =>
            play.api.Logger(getClass).error(s"Reaction toggle error: ${e.getMessage}", e)
            InternalServerError(write(ErrorResponse("リアクションの処理に失敗しました")))
              .as("application/json;charset=utf-8")
          }

      case scala.util.Failure(_) =>
        Future.successful(
          BadRequest(write(ErrorResponse("Invalid JSON format")))
            .as("application/json;charset=utf-8")
        )
    }
  }

  def toggleComment: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val bodyText = Json.stringify(request.body)

    scala.util.Try(read[ReactionToggleRequest](bodyText)) match {
      case scala.util.Success(req) =>
        val userId = request.userId

        if (req.contentTypeId == "4") {
          reactionService
            .toggleCommentReaction(req, userId)
            .map { response =>
              Ok(write(response)).as("application/json;charset=utf-8")
            }
            .recover { case e: Exception =>
              play.api.Logger(getClass).error(s"Comment reaction toggle error: ${e.getMessage}", e)
              InternalServerError(write(ErrorResponse("コメントリアクションの処理に失敗しました")))
                .as("application/json;charset=utf-8")
            }
        } else {
          Future.successful(
            BadRequest(write(ErrorResponse("Invalid contentTypeId for comment reaction")))
              .as("application/json;charset=utf-8")
          )
        }

      case scala.util.Failure(_) =>
        Future.successful(
          BadRequest(write(ErrorResponse("Invalid JSON format")))
            .as("application/json;charset=utf-8")
        )
    }
  }
}
