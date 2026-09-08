package integration

import com.typesafe.config.ConfigFactory
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration}
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.guice.GuiceApplicationBuilder
import slick.jdbc.MySQLProfile

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class DatabaseIntegrationSpec extends PlaySpec with GuiceOneAppPerSuite {
  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .loadConfig(
        Configuration(
          ConfigFactory.parseResources("application.test.conf").withFallback(ConfigFactory.load()).resolve()
        )
      )
      .build()

  private val database = app.injector.instanceOf[DatabaseConfigProvider].get[MySQLProfile].db
  import slick.jdbc.MySQLProfile.api.*

  "test database" should {
    "connect to the dedicated test database with the Evolutions schema applied" in {
      val databaseName = Await.result(database.run(sql"SELECT DATABASE()".as[String].head), 5.seconds)
      val roleCount = Await.result(database.run(sql"SELECT COUNT(*) FROM Roles".as[Int].head), 5.seconds)

      databaseName mustBe "lexi_test_db"
      roleCount must be >= 0
    }
  }
}
