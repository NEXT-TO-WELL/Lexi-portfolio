package integration

import java.time.Clock
import models.db.{TableQueries, User}
import org.mindrot.jbcrypt.BCrypt
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.UserRepository
import slick.jdbc.MySQLProfile

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class AuthTokenIntegrationSpec extends DatabaseIntegrationSpec {
  private val database = app.injector.instanceOf[DatabaseConfigProvider].get[MySQLProfile].db
  private val userRepository = app.injector.instanceOf[UserRepository]
  private val configuration = app.injector.instanceOf[Configuration]
  import slick.jdbc.MySQLProfile.api.*

  "AuthenticatedAction" should {
    "reject a token after its server-side version is incremented" in {
      val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(12)
      val userId = s"token-test-$suffix"
      val user = User(
        userId,
        suffix,
        s"token-$suffix",
        BCrypt.hashpw("password", BCrypt.gensalt()),
        "user",
        Some(System.currentTimeMillis())
      )
      Await.result(database.run(TableQueries.users += user), 5.seconds)

      try {
        val token = issueToken(user)
        status(route(app, authenticatedProfileRequest(token)).value) mustBe OK

        Await.result(userRepository.incrementTokenVersion(userId), 5.seconds)
        status(route(app, authenticatedProfileRequest(token)).value) mustBe UNAUTHORIZED
      } finally Await.result(database.run(TableQueries.users.filter(_.userId === userId).delete), 5.seconds)
    }
  }

  private def authenticatedProfileRequest(token: String) =
    FakeRequest(GET, "/user/profile").withHeaders(AUTHORIZATION -> s"Bearer $token")

  private def issueToken(user: User): String = {
    implicit val clock: Clock = Clock.systemUTC
    val claim = JwtClaim(
      content = Json.obj("userId" -> user.userId, "role" -> user.roleId, "tokenVersion" -> user.tokenVersion).toString
    ).by(configuration.get[String]("jwt.issuer"))
      .issuedNow
      .expiresIn(300)

    JwtJson.encode(claim, configuration.get[String]("jwt.secret"), JwtAlgorithm.HS256)
  }
}
