package controllers

import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents}

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import upickle.default.*
import play.api.Configuration
import play.api.Logger
import services.*
import models.*
import utils.JwtUtil
import actions.AuthenticatedAction
import shared.api.ErrorResponse

@Singleton
class WordController @Inject() (
    val controllerComponents: ControllerComponents,
    wordService: WordService,
    userService: UserService,
    authenticatedAction: AuthenticatedAction,
    config: Configuration
)(implicit ec: ExecutionContext)
    extends BaseController {

  // WordController.scala
  private def logger = Logger(getClass)

  def findWordsByKeyword(keyword: Option[String], wordId: Option[Long], tagId: Option[Long]): Action[AnyContent] =
    Action.async { implicit request =>
      val currentUserIdOpt = request.headers
        .get("Authorization")
        .collect { case h if h.startsWith("Bearer ") => h.stripPrefix("Bearer ") }
        .flatMap(token => utils.JwtUtil.decode(token, config))

      // Service側へ ID も渡すように変更
      wordService.findWordsByKeyword(keyword, wordId, tagId, currentUserIdOpt).map { response =>
        Ok(write(response)).as("application/json")
      }
    }

  // findMeanings や findExamples も同様に修正
  def findMeanings(wordId: Long, tagId: Long): Action[AnyContent] = Action.async { implicit request =>
    val maybeUserId = request.headers
      .get("Authorization")
      .collect { case h if h.startsWith("Bearer ") => h.stripPrefix("Bearer ") }
      .flatMap(token => utils.JwtUtil.decode(token, config)) // ここも config を追加

    wordService.getMeaningsWithStatus(wordId, tagId, maybeUserId).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  /** ページネーション対応：語釈を取得 */
  def findMeaningsPaginated(wordId: Long, tagId: Long, lastId: Option[Long], limit: Option[Int]): Action[AnyContent] =
    Action.async { implicit request =>
      val currentUserIdOpt = request.headers
        .get("Authorization")
        .collect { case h if h.startsWith("Bearer ") => h.stripPrefix("Bearer ") }
        .flatMap(token => JwtUtil.decode(token, config))

      wordService
        .getMeaningsWithStatusPaginated(
          wordId,
          tagId,
          currentUserIdOpt,
          lastId,
          utils.Validation.boundedLimit(limit, 10)
        )
        .map { response =>
          Ok(write(response)).as(JSON)
        }
    }

  def findExamples(wordId: Long, tagId: Long): Action[AnyContent] = Action.async { implicit request =>
    val currentUserIdOpt = request.headers
      .get("Authorization")
      .collect { case h if h.startsWith("Bearer ") => h.stripPrefix("Bearer ") }
      .flatMap(token => JwtUtil.decode(token, config))
    wordService.getExamplesWithStatus(wordId, tagId, currentUserIdOpt).map { response =>
      Ok(write(response)).as(JSON)
    }
  }

  /** ページネーション対応：用例を取得 */
  def findExamplesPaginated(wordId: Long, tagId: Long, lastId: Option[Long], limit: Option[Int]): Action[AnyContent] =
    Action.async { implicit request =>
      val currentUserIdOpt = request.headers
        .get("Authorization")
        .collect { case h if h.startsWith("Bearer ") => h.stripPrefix("Bearer ") }
        .flatMap(token => JwtUtil.decode(token, config))

      wordService
        .getExamplesWithStatusPaginated(
          wordId,
          tagId,
          currentUserIdOpt,
          lastId,
          utils.Validation.boundedLimit(limit, 20)
        )
        .map { response =>
          Ok(write(response)).as(JSON)
        }
    }

  // もし認証必須にするなら
  def findWordTagDetail(wordId: Long, tagId: Long): Action[AnyContent] = Action.async { implicit request =>
    // 1. トークンがあればデコードしてユーザーIDを取得（なければ None）
    val userIdOpt = request.headers
      .get("Authorization")
      .collect { case h if h.startsWith("Bearer ") => h.stripPrefix("Bearer ") }
      .flatMap(token => JwtUtil.decode(token, config)) // JwtUtil側でOptionを返す想定

    // 2. Serviceを呼ぶ（userIdOptをそのまま渡す）
    wordService.findWordTagDetail(wordId, tagId, userIdOpt).map {
      case Some(response) =>
        // 3. ログインしている場合のみ閲覧履歴を記録
        userIdOpt.foreach(uid => userService.recordView(uid, wordId, tagId))
        Ok(write(response)).as("application/json; charset=utf-8")

      case None =>
        NotFound(write(ErrorResponse("404", "指定された言葉とタグの組み合わせが見つかりません")))
    }
  }

  def postWord: Action[String] = authenticatedAction.async(parse.tolerantText) { implicit request =>
    val decodeResult = scala.util.Try(read[WordTagPostRequest](request.body)).toEither
    val dateTime = System.currentTimeMillis()
    val currentUser = Some(request.userId)

    decodeResult match {
      case Left(e) =>
        logger.debug(s"Decode Error: ${e.getMessage}")
        Future.successful(
          BadRequest(write(ErrorResponse("不正なリクエスト形式です")))
            .as("application/json;charset=utf-8")
        )
      case Right(postRequest) =>
        wordService.postWord(postRequest, currentUser, dateTime).map {
          case Right(res) => Created(write(res)).as("application/json;charset=utf-8")
          case Left(err)  => BadRequest(write(err)).as("application/json;charset=utf-8")
        }
    }
  }

  def postExample: Action[String] = authenticatedAction.async(parse.tolerantText) { implicit request =>
    scala.util.Try(read[ExamplePostRequest](request.body)) match {
      case scala.util.Success(req) =>
        val currentUserId = Some(request.userId)
        val now = System.currentTimeMillis()
        wordService.postExample(req, currentUserId, now).map {
          case Right(model) => Ok(write(model)).as("application/json;charset=utf-8")
          case Left(error)  => InternalServerError(write(error)).as("application/json;charset=utf-8")
        }

      case scala.util.Failure(_) =>
        val errorResp = ErrorResponse("Invalid JSON format")
        Future.successful(BadRequest(write(errorResp)).as("application/json;charset=utf-8"))
    }
  }

  // 1. 意味の投稿 (postMeaning)
  def postMeaning: Action[String] = authenticatedAction.async(parse.tolerantText) { implicit request =>
    scala.util.Try(read[MeaningPostRequest](request.body)) match {
      case scala.util.Success(req) =>
        val currentUserId = Some(request.userId) // authenticatedActionから取得
        val now = System.currentTimeMillis()

        wordService.postMeaning(req, currentUserId, now).map {
          case Right(model) => Ok(write(model)).as("application/json;charset=utf-8")
          case Left(error)  => InternalServerError(write(error)).as("application/json;charset=utf-8")
        }
      case scala.util.Failure(_) =>
        val errorResp = ErrorResponse("Invalid JSON format")
        Future.successful(BadRequest(write(errorResp)).as("application/json;charset=utf-8"))
    }
  }

  // 2. 言葉のサジェスト (suggestWords)
  def suggestWords: Action[String] = Action.async(parse.tolerantText) { implicit request =>
    val req = scala.util
      .Try(read[SuggestionRequest](request.body))
      .getOrElse(SuggestionRequest("", None, None))

    wordService.getSuggestions(req, "word").map { res =>
      Ok(write(res)).as("application/json;charset=utf-8")
    }
  }

  // タグのサジェスト
  def suggestTags: Action[String] = Action.async(parse.tolerantText) { implicit request =>
    val req = scala.util
      .Try(read[SuggestionRequest](request.body))
      .getOrElse(SuggestionRequest("", None, None))

    wordService.getSuggestions(req, "tag").map { res =>
      Ok(write(res)).as("application/json;charset=utf-8")
    }
  }

  def suggest: Action[String] = Action.async(parse.tolerantText) { request =>
    // 1. 届いた文字列をそのまま uPickle でパース
    val req = Try(read[SearchSuggestRequest](request.body))
      .getOrElse(SearchSuggestRequest("", None, None))

    // 2. サービスに丸投げ
    wordService.getSearchSuggestions(req).map { res =>
      // 3. サービスの結果(型)を書き出して、そのまま返す
      Ok(write(res)).as("application/json;charset=utf-8")
    }
  }

  // 語釈の削除
  def deleteMeaning(meaningId: Long): Action[AnyContent] = authenticatedAction.async { implicit request =>
    wordService.deleteMeaning(meaningId, request.userId).map {
      case Right(_)  => NoContent
      case Left(err) =>
        err.code match {
          case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
          case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
          case _     => InternalServerError(write(err)).as("application/json;charset=utf-8")
        }
    }
  }

  // 用例の削除
  def deleteExample(exampleId: Long): Action[AnyContent] = authenticatedAction.async { implicit request =>
    wordService.deleteExample(exampleId, request.userId).map {
      case Right(_)  => NoContent
      case Left(err) =>
        err.code match {
          case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
          case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
          case _     => InternalServerError(write(err)).as("application/json;charset=utf-8")
        }
    }
  }

  // 語釈の編集
  def updateMeaning(meaningId: Long): Action[String] = authenticatedAction.async(parse.tolerantText) {
    implicit request =>
      scala.util.Try(read[models.MeaningPostRequest](request.body)) match {
        case scala.util.Success(req) =>
          wordService.updateMeaning(meaningId, req.meaningText, request.userId).map {
            case Right(view) => Ok(write(view)).as("application/json;charset=utf-8")
            case Left(err)   =>
              err.code match {
                case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
                case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
                case _     => BadRequest(write(err)).as("application/json;charset=utf-8")
              }
          }
        case scala.util.Failure(_) =>
          val errorResp = ErrorResponse("Invalid JSON format")
          Future.successful(BadRequest(write(errorResp)).as("application/json;charset=utf-8"))
      }
  }

  // 用例の編集
  def updateExample(exampleId: Long): Action[String] = authenticatedAction.async(parse.tolerantText) {
    implicit request =>
      scala.util.Try(read[models.ExamplePostRequest](request.body)) match {
        case scala.util.Success(req) =>
          wordService.updateExample(exampleId, req.exampleText, request.userId).map {
            case Right(view) => Ok(write(view)).as("application/json;charset=utf-8")
            case Left(err)   =>
              err.code match {
                case "403" => Forbidden(write(err)).as("application/json;charset=utf-8")
                case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
                case _     => BadRequest(write(err)).as("application/json;charset=utf-8")
              }
          }
        case scala.util.Failure(_) =>
          val errorResp = ErrorResponse("Invalid JSON format")
          Future.successful(BadRequest(write(errorResp)).as("application/json;charset=utf-8"))
      }
  }
}
