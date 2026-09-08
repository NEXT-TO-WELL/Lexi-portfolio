package actions

import play.api.mvc.*
import pdi.jwt.{Jwt, JwtAlgorithm}
import upickle.default.*
import shared.api.ErrorResponse
import play.api.Configuration
import repositories.UserRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class AdminAuthenticatedAction @Inject() (
    val parser: BodyParsers.Default,
    config: Configuration,
    userRepository: UserRepository
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AdminAuthRequest, AnyContent] {

  // 環境変数または設定ファイルから秘密鍵を取得
  private val secretKey = config
    .getOptional[String]("admin.jwt.secret")
    .getOrElse(config.get[String]("jwt.secret"))
  private val expectedIssuer = config.get[String]("jwt.issuer")

  override def invokeBlock[A](request: Request[A], block: AdminAuthRequest[A] => Future[Result]): Future[Result] = {
    request.headers.get("Authorization") match {
      case Some(authHeader) if authHeader.startsWith("Bearer ") =>
        val token = authHeader.substring(7)
        Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS256)) match {
          case Success(claim) if claim.issuer.contains(expectedIssuer) =>
            try {
              val content = ujson.read(claim.content)
              val userId = content("userId").str
              val tokenVersion = content("tokenVersion").num.toInt

              userRepository.findById(userId).flatMap {
                case Some(user) if user.roleId == "admin" && user.tokenVersion == tokenVersion =>
                  block(AdminAuthRequest(userId, user.roleId, request))
                case Some(_) =>
                  Future.successful(
                    Results
                      .Forbidden(write(ErrorResponse("403", "管理者権限が必要です")))
                      .as("application/json;charset=utf-8")
                  )
                case None => unauthorized
              }
            } catch {
              case _: Exception => unauthorized
            }
          case _ => unauthorized
        }
      case _ => unauthorized
    }
  }

  private def unauthorized: Future[Result] =
    Future.successful(
      Results.Unauthorized(write(ErrorResponse("401", "認証が必要です"))).as("application/json;charset=utf-8")
    )
}

case class AdminAuthRequest[A](userId: String, role: String, request: Request[A]) extends WrappedRequest[A](request)
