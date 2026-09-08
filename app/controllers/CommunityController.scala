package controllers

import javax.inject.*
import play.api.mvc.*
import upickle.default.*

import scala.concurrent.{ExecutionContext, Future}
import models.*
import play.api.libs.json.JsValue
import services.*
import play.api.Configuration

import scala.util.Try
import actions.AuthenticatedAction
import shared.api.ErrorResponse

@Singleton
class CommunityController @Inject() (
    val controllerComponents: ControllerComponents,
    communityService: CommunityService,
    authenticatedAction: AuthenticatedAction,
    config: Configuration
)(implicit ec: ExecutionContext)
    extends BaseController {

  def findThreads(wordId: Long, tagId: Long): Action[AnyContent] = Action.async { implicit request =>
    communityService.findThreads(wordId, tagId).map { response =>
      Ok(write(response)).as("application/json")
    }
  }

  /** ページネーション対応：スレッドを取得 */
  def findThreadsPaginated(wordId: Long, tagId: Long, lastId: Option[Long], limit: Option[Int]): Action[AnyContent] =
    Action.async { implicit request =>
      communityService.findThreadsPaginated(wordId, tagId, lastId, utils.Validation.boundedLimit(limit, 10)).map {
        response =>
          Ok(write(response)).as("application/json")
      }
    }

  // 認証なしの暫定版
  def postThread: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val currentUserId = request.userId

    // 1. JSONパースを Try で試行
    scala.util.Try(read[ThreadPostRequest](request.body.toString())).toEither match {
      case Left(e) =>
        // JSONが不正な場合
        play.api.Logger(getClass).debug(s"Thread post JSON parse error: ${e.getMessage}")
        Future.successful(BadRequest(write(ErrorResponse("不正なリクエスト形式です"))))

      case Right(postReq) =>
        // 2. 正常にパースできたら Service を実行
        communityService
          .postThread(postReq, currentUserId)
          .map {
            case Right(threadView) => Created(write(threadView)).as(JSON)
            case Left(error)       => BadRequest(write(error)).as(JSON)
          }
          .recover { case e: Exception =>
            // 3. DBエラーなど予期せぬエラーのハンドリング
            play.api.Logger(getClass).error(s"Thread post error: ${e.getMessage}", e)
            InternalServerError(write(ErrorResponse("スレッドの投稿に失敗しました")))
          }
    }
  }

//  def findMyThreads(userId: String): Action[AnyContent] = Action.async { implicit request =>
//    communityService.findMyThreads(userId).map { response =>
//      Ok(write(response)).as("application/json")
//    }
//  }

  // CommunityController.scala

  def findThreadDetail(threadId: Long): Action[AnyContent] = Action.async { implicit request =>
    // ログインしていれば Some(userId)、していなければ None
    val currentUserIdOpt = extractUserId(request)

    communityService.findThreadDetail(threadId, currentUserIdOpt).map {
      case Right(response) =>
        Ok(write(response)).as("application/json;charset=utf-8")
      case Left(error) =>
        NotFound(write(error)).as("application/json;charset=utf-8")
    }
  }

  /** ページネーション対応：コメントを取得 */
  def findCommentsPaginated(threadId: Long, lastId: Option[Long], limit: Option[Int]): Action[AnyContent] =
    Action.async { implicit request =>
      val currentUserIdOpt = extractUserId(request)

      communityService
        .findCommentsPaginated(threadId, currentUserIdOpt, lastId, utils.Validation.boundedLimit(limit, 10))
        .map { response =>
          Ok(write(response)).as("application/json")
        }
    }

  def postComment: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    // 1. 認証済みユーザーIDを取得
    val currentUserId = request.userId

    val result: Future[Result] = for {
      // 2. JSONパース (Try -> Future)
      req <- Future.fromTry(Try(read[CommentPostRequest](request.body.toString())))

      // 3. サービス呼び出し（取得したIDを渡す）
      serviceResult <- communityService.postComment(req, currentUserId)
    } yield {
      serviceResult match {
        case Right(view) => Created(write(view)).as(JSON)
        case Left(err)   => BadRequest(write(err)).as(JSON)
      }
    }

    // 4. エラーハンドリング（パースエラーやDBエラー）
    result.recover {
      case e: upickle.core.AbortException =>
        play.api.Logger(getClass).debug(s"Comment post JSON parse error: ${e.getMessage}")
        BadRequest(write(ErrorResponse("不正なリクエスト形式です"))).as(JSON)
      case e: Exception =>
        play.api.Logger(getClass).error(s"Comment post error: ${e.getMessage}", e)
        InternalServerError(write(ErrorResponse("コメントの投稿に失敗しました"))).as(JSON)
    }
  }

  def resolveThread: Action[JsValue] = authenticatedAction.async(parse.json) { implicit request =>
    val currentUserId = request.userId

    // uPickleでリクエストボディを読み込み
    scala.util.Try(read[ResolveThreadRequest](request.body.toString())).toEither match {
      case Left(e) =>
        play.api.Logger(getClass).debug(s"Resolve thread JSON parse error: ${e.getMessage}")
        Future.successful(BadRequest(write(ErrorResponse("400", "不正なリクエスト形式です"))))

      case Right(req) =>
        communityService.resolveThread(req, currentUserId).map {
          case Right(_)  => Ok(write(Map("status" -> "success"))).as(JSON)
          case Left(err) =>
            err.code match {
              case "403" => Forbidden(write(err)).as(JSON)
              case "404" => NotFound(write(err)).as(JSON)
              case _     => BadRequest(write(err)).as(JSON)
            }
        }
    }
  }

//  def findMyComments(userId: String): Action[AnyContent] = Action.async { implicit request =>
//    communityService.findMyComments(userId).map { response =>
//      Ok(write(response)).as("application/json")
//    }
//  }

  // スレッドの削除
  def deleteThread(threadId: Long): Action[AnyContent] = authenticatedAction.async { implicit request =>
    communityService.deleteThread(threadId, request.userId).map {
      case Right(_)  => NoContent
      case Left(err) =>
        err.code match {
          case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
          case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
          case _     => InternalServerError(write(err)).as("application/json;charset=utf-8")
        }
    }
  }

  // スレッドの更新
  def updateThread(threadId: Long): Action[String] = authenticatedAction.async(parse.tolerantText) { implicit request =>
    scala.util.Try(read[ThreadUpdateRequest](request.body)) match {
      case scala.util.Success(req) =>
        communityService.updateThread(threadId, req.threadTitle, request.userId).map {
          case Right(view) => Ok(write(view)).as("application/json;charset=utf-8")
          case Left(err)   =>
            err.code match {
              case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
              case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
              case _     => BadRequest(write(err)).as("application/json;charset=utf-8")
            }
        }
      case scala.util.Failure(ex) =>
        play.api.Logger(getClass).debug(s"Update thread JSON parse error: ${ex.getMessage}")
        Future.successful(BadRequest(write(ErrorResponse("400", "不正なリクエスト形式です"))).as("application/json;charset=utf-8"))
    }
  }

  // コメントの削除
  def deleteComment(commentId: Long): Action[AnyContent] = authenticatedAction.async { implicit request =>
    communityService.deleteComment(commentId, request.userId).map {
      case Right(_)  => NoContent
      case Left(err) =>
        err.code match {
          case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
          case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
          case _     => InternalServerError(write(err)).as("application/json;charset=utf-8")
        }
    }
  }

  // 補助メソッド：トークンから userId をオプションで取得
  private def extractUserId(request: RequestHeader): Option[String] = {
    val secretKey = config.get[String]("jwt.secret")
    val expectedIssuer = config.get[String]("jwt.issuer")

    for {
      authHeader <- request.headers.get("Authorization")
      if authHeader.startsWith("Bearer ")
      token = authHeader.substring(7)
      claim <- pdi.jwt.JwtJson.decode(token, secretKey, Seq(pdi.jwt.JwtAlgorithm.HS256)).toOption
      if claim.issuer.contains(expectedIssuer)
      json = play.api.libs.json.Json.parse(claim.content)
      userId <- (json \ "userId").asOpt[String]
    } yield userId
  }

  // コメントの更新
  def updateComment(commentId: Long): Action[String] = authenticatedAction.async(parse.tolerantText) {
    implicit request =>
      // 1. JSONパース（シンプルな { "commentText": "..." } を想定）
      scala.util.Try(read[CommentUpdateByTextRequest](request.body)) match {
        case scala.util.Success(req) =>
          // 2. サービス層の呼び出し
          communityService.updateComment(commentId, req.commentText, request.userId).map {
            case Right(view) => Ok(write(view)).as("application/json;charset=utf-8")
            case Left(err)   =>
              err.code match {
                case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
                case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
                case _     => BadRequest(write(err)).as("application/json;charset=utf-8")
              }
          }
        case scala.util.Failure(ex) =>
          play.api.Logger(getClass).debug(s"Update comment JSON parse error: ${ex.getMessage}")
          Future.successful(
            BadRequest(write(ErrorResponse("400", "不正なリクエスト形式です"))).as("application/json;charset=utf-8")
          )
      }
  }
}
