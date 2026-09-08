package actions

import javax.inject.Inject
import play.api.mvc.*
import play.api.Configuration
import pdi.jwt.{JwtAlgorithm, JwtJson}
import repositories.UserRepository

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json

case class UserRequest[A](userId: String, request: Request[A]) extends WrappedRequest[A](request)

class AuthenticatedAction @Inject() (
    val parser: BodyParsers.Default,
    config: Configuration,
    userRepository: UserRepository
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[UserRequest, AnyContent] { // 戻り値を UserRequest に指定

  override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] = {
    val secretKey = config.get[String]("jwt.secret")
    val expectedIssuer = config.get[String]("jwt.issuer")

    val maybeToken = request.headers
      .get("Authorization")
      .collect { case h if h.startsWith("Bearer ") => h.substring(7) }

    val decodedClaim = maybeToken.flatMap { token =>
      JwtJson.decode(token, secretKey, Seq(JwtAlgorithm.HS256)).toOption
    }

    val tokenIdentity = decodedClaim.flatMap { claim =>
      if (!claim.issuer.contains(expectedIssuer)) None
      else {
        scala.util.Try {
          val json = Json.parse(claim.content)
          ((json \ "userId").as[String], (json \ "tokenVersion").as[Int])
        }.toOption
      }
    }

    tokenIdentity match {
      case Some((userId, tokenVersion)) =>
        userRepository.findById(userId).flatMap {
          case Some(user) if user.tokenVersion == tokenVersion => block(UserRequest(userId, request))
          case _                                               => unauthorized
        }
      case None => unauthorized
    }
  }

  private def unauthorized: Future[Result] =
    Future.successful(Results.Unauthorized(Json.obj("status" -> "error", "message" -> "Authentication required")))
}
