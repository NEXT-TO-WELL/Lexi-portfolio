package controllers

import models.{FavoriteToggleRequest, FavoriteToggleResponse}
import play.api.libs.json.*
import play.api.mvc.{Action, *}
import upickle.default.*
import services.UserService
import actions.AuthenticatedAction
import shared.api.ErrorResponse

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */
@Singleton
class UserController @Inject() (
    val controllerComponents: ControllerComponents,
    authenticatedAction: AuthenticatedAction,
    userService: UserService
)(implicit ec: ExecutionContext)
    extends BaseController {

  /** Create an Action to render an HTML page.
    *
    * The configuration in the `routes` file means that this method will be called when the application receives a `GET`
    * request with a path of `/`.
    */
  def getMyProfile: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getMyProfile(request.userId).map { response =>
      Ok(write(response)).as("application/json;charset=utf-8")
    }
  }

  def getMyHistory: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getUserHistory(request.userId).map { response =>
      Ok(write(response)).as("application/json")
    }
  }

  def getMyExamples: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val currentUserId = request.userId
    userService.getMyExamples(currentUserId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def getMyMeanings: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val currentUserId = request.userId
    userService.getMyMeanings(currentUserId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def getMyWords: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val currentUserId = request.userId
    userService.getMyWords(currentUserId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def getMyComments: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val currentUserId = request.userId
    userService.getMyComments(currentUserId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def getMyThreads: Action[AnyContent] = authenticatedAction.async { implicit request =>
    val currentUserId = request.userId
    userService.getMyThreads(currentUserId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  // UserController.scala

  def getMyFavoriteMeanings: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getMyFavoriteMeanings(request.userId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def getMyFavoriteExamples: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getMyFavoriteExamples(request.userId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def getMyReactedMeanings: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getMyReactedMeanings(request.userId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  def getMyReactedExamples: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getMyReactedExamples(request.userId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  // GET /user/follows/tags
  // GET /user/follows/tags
  def getFollowedTags: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getFollowedTags(request.userId).map {
      case Right(tagsResponse) => // ここで 'tagsResponse' と定義した場合
        Ok(write(tagsResponse)).as("application/json") // ここも一致させる
      case Left(error) =>
        InternalServerError(write(error)).as("application/json")
    }
  }

  def getFollowedWords: Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.getFollowedWords(request.userId).map {
      case Right(wordsResponse) =>
        Ok(write(wordsResponse)).as("application/json")
      case Left(error) =>
        InternalServerError(write(error)).as("application/json")
    }
  }

  def followTag: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val userId = request.userId // 認証済みID
    val tagId = (request.body \ "tagId").as[Long]

    userService
      .followTag(userId, tagId)
      .map { follow =>
        Created(write(follow)).as(JSON)
      }
      .recover { case e: Exception =>
        play.api.Logger(getClass).error(s"Follow tag error: ${e.getMessage}", e)
        InternalServerError(write(ErrorResponse("タグのフォローに失敗しました"))).as(JSON)
      }
  }

  /** 単語のフォロー (トグル操作) POST /api/follows/words/toggle
    */
  def toggleWordFollow: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val userId = request.userId
    val tagId = (request.body \ "tagId").as[Long]
    val wordId = (request.body \ "wordId").as[Long]

    userService.toggleWordFollow(userId, tagId, wordId).map { isFollowed =>
      Ok(write(Map("isFollowed" -> isFollowed))).as(JSON)
    }
  }

  /** フォロー解除 (個別削除) DELETE /api/follows/:followId
    */
  def unfollow(followId: Long): Action[AnyContent] = authenticatedAction.async { implicit request =>
    userService.unfollow(followId, request.userId).map {
      case true  => NoContent
      case false => NotFound(write(ErrorResponse("Follow record not found"))).as(JSON)
    }
  }

  // UserController.scala

  def toggleFavorite: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val bodyText = Json.stringify(request.body)

    scala.util.Try(read[FavoriteToggleRequest](bodyText)) match {
      case scala.util.Success(req) =>
        userService.toggleFavorite(request.userId, req.contentTypeId, req.contentId).map { isFavorite =>
          // 指定された FavoriteToggleResponse 型で返す
          val response = FavoriteToggleResponse(isFavorite = isFavorite)
          Ok(write(response)).as("application/json;charset=utf-8")
        }
      case scala.util.Failure(_) =>
        Future.successful(BadRequest(write(ErrorResponse("Invalid JSON format"))).as(JSON))
    }
  }

//  def getMyFavorites:Action[AnyContent] = authenticatedAction.async{ implicit  request =>
//    val currentUserId = request.userId
//    userService.getMyFavorites(currentUserId).map { response =>
//      Ok(write(response)).as(JSON)
//    }
//  }

  def updateUserName: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val bodyText = Json.stringify(request.body)

    case class UpdateUserNameRequest(newUserName: String) derives ReadWriter

    scala.util.Try(read[UpdateUserNameRequest](bodyText)) match {
      case scala.util.Success(req) =>
        userService.updateUserName(request.userId, req.newUserName).map {
          case Right(_)    => Ok(write(Map("success" -> true))).as("application/json")
          case Left(error) => BadRequest(write(error)).as("application/json")
        }
      case scala.util.Failure(_) =>
        Future.successful(BadRequest(write(ErrorResponse("Invalid JSON format"))).as("application/json"))
    }
  }

  def updatePassword: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val bodyText = Json.stringify(request.body)

    case class UpdatePasswordRequest(currentPassword: String, newPassword: String) derives ReadWriter

    scala.util.Try(read[UpdatePasswordRequest](bodyText)) match {
      case scala.util.Success(req) =>
        userService.updatePassword(request.userId, req.currentPassword, req.newPassword).map {
          case Right(_)    => Ok(write(Map("success" -> true))).as("application/json")
          case Left(error) => BadRequest(write(error)).as("application/json")
        }
      case scala.util.Failure(_) =>
        Future.successful(BadRequest(write(ErrorResponse("Invalid JSON format"))).as("application/json"))
    }
  }

  def deleteAccount: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val bodyText = Json.stringify(request.body)

    case class DeleteAccountRequest(password: String) derives ReadWriter

    scala.util.Try(read[DeleteAccountRequest](bodyText)) match {
      case scala.util.Success(req) =>
        userService.deleteAccount(request.userId, req.password).map {
          case Right(_)    => Ok(write(Map("success" -> true))).as("application/json")
          case Left(error) => BadRequest(write(error)).as("application/json")
        }
      case scala.util.Failure(_) =>
        Future.successful(BadRequest(write(ErrorResponse("Invalid JSON format"))).as("application/json"))
    }
  }
}
