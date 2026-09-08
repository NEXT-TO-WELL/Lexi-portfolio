package integration

import models.db.{TableQueries, User}
import play.api.db.slick.DatabaseConfigProvider
import repositories.PushSubscriptionRepository
import services.PushSubscriptionService
import shared.api.PushSubscriptionRequest
import slick.jdbc.MySQLProfile

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class PushSubscriptionIntegrationSpec extends DatabaseIntegrationSpec {
  private val database = app.injector.instanceOf[DatabaseConfigProvider].get[MySQLProfile].db
  private val repository = app.injector.instanceOf[PushSubscriptionRepository]
  private val service = app.injector.instanceOf[PushSubscriptionService]
  import slick.jdbc.MySQLProfile.api.*

  "PushSubscriptionRepository" should {
    "keep subscriptions per browser and enforce user-bound soft deletion" in {
      val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(12)
      val firstUser = s"push-a-$suffix"
      val secondUser = s"push-b-$suffix"
      val firstEndpoint = s"https://push.example.test/$suffix/first"
      val secondEndpoint = s"https://push.example.test/$suffix/second"
      val now = System.currentTimeMillis()

      Await.result(
        database.run(
          TableQueries.users ++= Seq(
            User(firstUser, s"pa$suffix", s"push-a-$suffix", "test", "user", Some(now)),
            User(secondUser, s"pb$suffix", s"push-b-$suffix", "test", "user", Some(now))
          )
        ),
        5.seconds
      )

      try {
        Await.result(
          repository.saveForUser(firstUser, firstEndpoint, "p256dh-a", "auth-a", Some("browser-a")),
          5.seconds
        )
        Await.result(
          repository.saveForUser(firstUser, secondEndpoint, "p256dh-b", "auth-b", Some("browser-b")),
          5.seconds
        )
        Await.result(repository.findActiveForUser(firstUser), 5.seconds) must have size 2

        Await.result(repository.deactivateForUser(secondUser, firstEndpoint), 5.seconds) mustBe false
        Await.result(repository.findActiveForUser(firstUser), 5.seconds) must have size 2

        Await.result(repository.deactivateForUser(firstUser, firstEndpoint), 5.seconds) mustBe true
        val deleted = Await.result(
          database.run(TableQueries.pushSubscriptions.filter(_.endpoint === firstEndpoint).result.head),
          5.seconds
        )
        deleted.isDeleted mustBe true
        deleted.deletedAt must not be empty

        Await.result(repository.saveForUser(secondUser, firstEndpoint, "p256dh-new", "auth-new", None), 5.seconds)
        val reassigned = Await.result(repository.findActiveForUser(secondUser), 5.seconds).head
        reassigned.userId mustBe secondUser
        reassigned.isDeleted mustBe false
        reassigned.deletedAt mustBe empty
      } finally {
        Await.result(
          database.run(TableQueries.users.filter(_.userId.inSet(Set(firstUser, secondUser))).delete),
          5.seconds
        )
      }
    }

    "soft-delete subscriptions rejected permanently by a push provider" in {
      val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(12)
      val userId = s"push-failure-$suffix"
      val endpoint = s"https://push.example.test/$suffix/failure"
      val now = System.currentTimeMillis()

      Await.result(
        database.run(
          TableQueries.users += User(userId, s"pf$suffix", s"push-failure-$suffix", "test", "user", Some(now))
        ),
        5.seconds
      )

      try {
        val subscription = Await.result(repository.saveForUser(userId, endpoint, "p256dh", "auth", None), 5.seconds)
        Await.result(repository.recordFailure(subscription.subscriptionId.get, permanentlyInvalid = true), 5.seconds)

        Await.result(repository.findActiveForUser(userId), 5.seconds) mustBe empty
        val failed = Await.result(
          database.run(
            TableQueries.pushSubscriptions.filter(_.subscriptionId === subscription.subscriptionId.get).result.head
          ),
          5.seconds
        )
        failed.failureCount mustBe 1
        failed.lastFailureAt must not be empty
        failed.deletedAt must not be empty
      } finally {
        Await.result(database.run(TableQueries.users.filter(_.userId === userId).delete), 5.seconds)
      }
    }
  }

  "PushSubscriptionService" should {
    "not accept subscriptions when Web Push is not fully configured" in {
      service.publicConfig.enabled mustBe false

      val failure = intercept[IllegalArgumentException] {
        Await.result(
          service.subscribe(
            "unused-user",
            PushSubscriptionRequest("https://push.example.test/unused", "p256dh", "auth")
          ),
          5.seconds
        )
      }
      failure.getMessage mustBe "Web Push is disabled"
    }
  }
}
