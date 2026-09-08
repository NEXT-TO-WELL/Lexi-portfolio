package controllers

import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import play.api.Configuration
import play.filters.csrf.CSRFAddToken
import services.AuthService
import actions.{AdminAuthenticatedAction, AuthenticatedAction}
import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger

@Singleton
class AuthController @Inject() (
    val controllerComponents: ControllerComponents,
    config: Configuration,
    addToken: CSRFAddToken,
    authService: AuthService,
    authenticatedAction: AuthenticatedAction,
    adminAuthenticatedAction: AdminAuthenticatedAction
)(implicit ec: ExecutionContext)
    extends BaseController { // implicit ec を追加

  implicit val clock: Clock = Clock.systemUTC
  // 1. サインイン（既存のダミーロジックを authService 連携に置き換え可能）
  // 1. サインイン
  private val logger = Logger(classOf[AuthController])

  def signIn: Action[JsValue] = Action.async(parse.json) { request =>
    val userIdOpt = (request.body \ "userId").asOpt[String]
    val passwordOpt = (request.body \ "password").asOpt[String]

    (userIdOpt, passwordOpt) match {
      case (Some(uid), Some(pw)) =>
        authService.authenticate(uid, pw).map {
          case Right(user) =>
            // 管理者ロールのログインをブロック（管理者は専用エンドポイントを使用）
            if (user.roleId == "admin") {
              logger.warn(s"Admin login attempt blocked from public endpoint: ${user.userId}")
              Forbidden(Json.obj("message" -> "管理者は専用の管理画面からログインしてください"))
            } else {
              // 成功ログ: 誰がログインしたか
              logger.info(s"Successfully signed in user: ${user.userId}")

              val claim = JwtClaim(
                content = Json
                  .obj("userId" -> user.userId, "role" -> user.roleId, "tokenVersion" -> user.tokenVersion)
                  .toString
              ).by(config.get[String]("jwt.issuer"))
                .issuedAt(System.currentTimeMillis() / 1000)
                .expiresIn(config.get[Long]("jwt.expiryInSeconds"))

              val token = JwtJson.encode(claim, config.get[String]("jwt.secret"), JwtAlgorithm.HS256)
              Ok(
                Json.obj(
                  "token" -> token,
                  "userId" -> user.userId,
                  "userName" -> user.userName,
                  "role" -> user.roleId,
                  "status" -> "success"
                )
              )
            }

          case Left(error) =>
            // 警告ログ: パスワード間違いなど（不正アクセスの監視に役立つ）
            logger.warn(s"Failed sign-in attempt for user: $uid - Reason: $error")
            Unauthorized(Json.obj("message" -> error))
        }
      case _ =>
        Future.successful(BadRequest(Json.obj("message" -> "入力が不足しています")))
    }
  }

  // 管理者専用ログインエンドポイント
  def adminSignIn: Action[JsValue] = Action.async(parse.json) { request =>
    val userIdOpt = (request.body \ "userId").asOpt[String]
    val passwordOpt = (request.body \ "password").asOpt[String]

    (userIdOpt, passwordOpt) match {
      case (Some(uid), Some(pw)) =>
        authService.authenticate(uid, pw).map {
          case Right(user) =>
            // 管理者ロールのみ許可
            if (user.roleId == "admin") {
              logger.info(s"Admin successfully signed in: ${user.userId}")

              // 管理者用の秘密鍵を使用（設定されている場合）
              val adminSecretKey = config
                .getOptional[String]("admin.jwt.secret")
                .getOrElse(config.get[String]("jwt.secret"))

              val claim = JwtClaim(
                content = Json
                  .obj("userId" -> user.userId, "role" -> user.roleId, "tokenVersion" -> user.tokenVersion)
                  .toString
              ).by(config.get[String]("jwt.issuer"))
                .issuedAt(System.currentTimeMillis() / 1000)
                .expiresIn(config.get[Long]("jwt.expiryInSeconds"))

              val token = JwtJson.encode(claim, adminSecretKey, JwtAlgorithm.HS256)
              Ok(
                Json.obj(
                  "token" -> token,
                  "userId" -> user.userId,
                  "userName" -> user.userName,
                  "role" -> user.roleId,
                  "status" -> "success"
                )
              )
            } else {
              logger.warn(s"Non-admin user attempted admin login: ${user.userId}")
              Forbidden(Json.obj("message" -> "管理者権限が必要です"))
            }

          case Left(error) =>
            logger.warn(s"Failed admin sign-in attempt for user: $uid - Reason: $error")
            Unauthorized(Json.obj("message" -> error))
        }
      case _ =>
        Future.successful(BadRequest(Json.obj("message" -> "入力が不足しています")))
    }
  }

  def signUp: Action[JsValue] = Action.async(parse.json) { request =>
    val userId = (request.body \ "userId").as[String]
    val userName = (request.body \ "userName").as[String]

    authService.signUp(userId, (request.body \ "password").as[String], userName).map {
      case Right(user) =>
        // 成功ログ: 新規登録
        logger.info(s"New user registered: ${user.userId} ($userName)")

        val claim = JwtClaim(
          content = Json
            .obj("userId" -> user.userId, "role" -> user.roleId, "tokenVersion" -> user.tokenVersion)
            .toString,
          issuer = Some(config.get[String]("jwt.issuer")),
          expiration = Some(System.currentTimeMillis() / 1000 + config.get[Long]("jwt.expiryInSeconds")),
          issuedAt = Some(System.currentTimeMillis() / 1000)
        )

        val token = JwtJson.encode(claim, config.get[String]("jwt.secret"), JwtAlgorithm.HS256)
        Created(
          Json.obj(
            "token" -> token,
            "userId" -> user.userId,
            "userName" -> user.userName,
            "role" -> user.roleId,
            "status" -> "success"
          )
        )

      case Left(errorMessage) =>
        // エラーログ: すでに存在するIDなど
        logger.error(s"Failed sign-up for user: $userId - Error: $errorMessage")
        Conflict(Json.obj("message" -> errorMessage))
    }
  }

  // 3. ログアウト
  def logout: Action[AnyContent] = authenticatedAction.async { request =>
    authService.logout(request.userId).map { _ =>
      logger.info(s"User logged out: ${request.userId}")
      Ok(Json.obj("message" -> "ログアウトしました"))
    }
  }

  def adminLogout: Action[AnyContent] = adminAuthenticatedAction.async { request =>
    authService.logout(request.userId).map { _ =>
      logger.info(s"Admin logged out: ${request.userId}")
      Ok(Json.obj("message" -> "ログアウトしました"))
    }
  }

  // 4. 画面表示
  def signInIndex(): Action[AnyContent] = addToken {
    Action { (request: Request[AnyContent]) =>
      implicit val req: RequestHeader = request
      Ok(views.html.signIn())
    }
  }
}
