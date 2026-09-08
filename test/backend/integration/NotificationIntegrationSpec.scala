package integration

import controllers.NotificationController
import models.{CommentModel, ExampleModel, MeaningModel}
import models.db.{
  NotificationModel,
  ReactionNotificationModel,
  SystemNotificationModel,
  TableQueries,
  TagModel,
  ThreadModel,
  User,
  Word,
  WordTagModel
}
import org.scalatest.concurrent.ScalaFutures
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.Results.Unauthorized
import play.api.test.FakeRequest
import repositories.NotificationModelRepository
import services.{NotificationService, ReactionService}
import shared.api.ReactionToggleRequest
import slick.jdbc.MySQLProfile

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class NotificationIntegrationSpec extends DatabaseIntegrationSpec with ScalaFutures {
  private val database = app.injector.instanceOf[DatabaseConfigProvider].get[MySQLProfile].db
  private val controller = app.injector.instanceOf[NotificationController]
  private val repository = app.injector.instanceOf[NotificationModelRepository]
  private val notificationService = app.injector.instanceOf[NotificationService]
  private val reactionService = app.injector.instanceOf[ReactionService]
  import slick.jdbc.MySQLProfile.api.*

  "POST /notifications/read-all" should {
    "reject an unauthenticated request" in {
      val result = controller.markAllAsRead.apply(FakeRequest("POST", "/notifications/read-all"))

      result.futureValue.header.status mustBe Unauthorized.header.status
    }

    "mark only the authenticated user's unread notifications as read" in {
      val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(12)
      val firstUserId = s"notification-a-$suffix"
      val secondUserId = s"notification-b-$suffix"
      val now = System.currentTimeMillis()
      val setup = DBIO.seq(
        TableQueries.users ++= Seq(
          User(firstUserId, s"a$suffix", s"notify-a-$suffix", "test", "user", Some(now)),
          User(secondUserId, s"b$suffix", s"notify-b-$suffix", "test", "user", Some(now))
        ),
        TableQueries.notifications ++= Seq(
          NotificationModel(None, firstUserId, now, false, "1"),
          NotificationModel(None, firstUserId, now + 1, false, "1"),
          NotificationModel(None, firstUserId, now + 2, true, "1"),
          NotificationModel(None, secondUserId, now + 3, false, "1")
        )
      )

      Await.result(database.run(setup.transactionally), 5.seconds)
      try {
        Await.result(repository.markAllAsRead(firstUserId), 5.seconds) mustBe 2

        Await.result(repository.getUnreadCount(firstUserId), 5.seconds) mustBe 0
        Await.result(repository.getUnreadCount(secondUserId), 5.seconds) mustBe 1
      } finally {
        val cleanup = for {
          _ <- TableQueries.notifications.filter(_.userId.inSet(Set(firstUserId, secondUserId))).delete
          _ <- TableQueries.users.filter(_.userId.inSet(Set(firstUserId, secondUserId))).delete
        } yield ()
        Await.result(database.run(cleanup.transactionally), 5.seconds)
      }
    }
  }

  "NotificationService" should {
    "display stored system text and resolve links for every content type" in withFixture { fixture =>
      val now = System.currentTimeMillis()
      val setup = for {
        systemId <- insertNotification(fixture.ownerId, now, "1")
        _ <- TableQueries.systemNotifications += SystemNotificationModel(systemId, "保存されたシステム通知")
        meaningId <- insertNotification(fixture.ownerId, now + 1, "2")
        _ <- TableQueries.reactionNotifications += ReactionNotificationModel(
          meaningId,
          fixture.actorId,
          "1",
          fixture.meaningId,
          1001
        )
        exampleId <- insertNotification(fixture.ownerId, now + 2, "2")
        _ <- TableQueries.reactionNotifications += ReactionNotificationModel(
          exampleId,
          fixture.actorId,
          "2",
          fixture.exampleId,
          1002
        )
        threadId <- insertNotification(fixture.ownerId, now + 3, "2")
        _ <- TableQueries.reactionNotifications += ReactionNotificationModel(
          threadId,
          fixture.actorId,
          "3",
          fixture.threadId,
          1003
        )
        commentId <- insertNotification(fixture.ownerId, now + 4, "2")
        _ <- TableQueries.reactionNotifications += ReactionNotificationModel(
          commentId,
          fixture.actorId,
          "4",
          fixture.commentId,
          1004
        )
      } yield ()
      Await.result(database.run(setup.transactionally), 5.seconds)

      val notifications = Await.result(notificationService.getNotificationsForUser(fixture.ownerId), 5.seconds)
      notifications.notifications.map(_.message) must contain("保存されたシステム通知")
      notifications.notifications.filter(_.notificationType == "REACTION").map(_.link).toSet mustBe Set(
        s"/word/${fixture.wordId}/tag/${fixture.tagId}/meaning",
        s"/word/${fixture.wordId}/tag/${fixture.tagId}/example",
        s"/threads/${fixture.threadId}"
      )
    }

    "store comment and best-answer notifications as type 4 without self notifications" in withFixture { fixture =>
      Await.result(
        notificationService.createCommentNotification(
          fixture.threadId,
          fixture.commentId,
          None,
          fixture.actorId
        ),
        5.seconds
      )
      Await.result(
        notificationService.createCommentNotification(
          fixture.threadId,
          fixture.commentId,
          None,
          fixture.ownerId
        ),
        5.seconds
      )
      Await.result(
        notificationService.createBestAnswerNotification(
          fixture.threadId,
          fixture.commentId,
          fixture.actorId,
          fixture.ownerId
        ),
        5.seconds
      )

      val ownerRows = Await.result(
        database.run(TableQueries.notifications.filter(_.userId === fixture.ownerId).result),
        5.seconds
      )
      ownerRows must have size 1
      ownerRows.head.notificationTypesId mustBe "4"

      val actorRows = Await.result(
        database.run(
          TableQueries.notifications
            .filter(_.userId === fixture.actorId)
            .join(TableQueries.commentNotifications)
            .on(_.notificationsId === _.notificationsId)
            .result
        ),
        5.seconds
      )
      actorRows must have size 1
      actorRows.head._1.notificationTypesId mustBe "4"
      actorRows.head._2.notificationKind mustBe "BEST_ANSWER"
    }

    "create notifications for supported reaction targets only when another user adds one" in withFixture { fixture =>
      val meaningRequest = ReactionToggleRequest("1", fixture.meaningId, "good")

      Await.result(reactionService.toggleReaction(meaningRequest, fixture.actorId), 5.seconds).active mustBe true
      Await.result(repository.getUnreadCount(fixture.ownerId), 5.seconds) mustBe 1

      Await.result(reactionService.toggleReaction(meaningRequest, fixture.actorId), 5.seconds).active mustBe false
      Await.result(repository.getUnreadCount(fixture.ownerId), 5.seconds) mustBe 1

      Await.result(reactionService.toggleReaction(meaningRequest, fixture.ownerId), 5.seconds).active mustBe true
      Await
        .result(
          reactionService.toggleReaction(ReactionToggleRequest("2", fixture.exampleId, "helpful"), fixture.actorId),
          5.seconds
        )
        .active mustBe true
      Await
        .result(
          reactionService.toggleReaction(ReactionToggleRequest("3", fixture.threadId, "funny"), fixture.actorId),
          5.seconds
        )
        .active mustBe true
      Await
        .result(
          reactionService.toggleCommentReaction(ReactionToggleRequest("4", fixture.commentId, "good"), fixture.ownerId),
          5.seconds
        )
        .active mustBe true

      Await.result(repository.getUnreadCount(fixture.ownerId), 5.seconds) mustBe 3
      Await.result(repository.getUnreadCount(fixture.actorId), 5.seconds) mustBe 1

      val contentTypes =
        Await.result(database.run(TableQueries.reactionNotifications.map(_.contentTypeId).result), 5.seconds)
      contentTypes.toSet must contain allOf ("1", "2", "3", "4")
    }
  }

  private final case class NotificationFixture(
      ownerId: String,
      actorId: String,
      wordId: Long,
      tagId: Long,
      meaningId: Long,
      exampleId: Long,
      threadId: Long,
      commentId: Long
  )

  private def withFixture(test: NotificationFixture => Unit): Unit = {
    val fixture = createFixture()
    try test(fixture)
    finally cleanupFixture(fixture)
  }

  private def insertNotification(userId: String, date: Long, typeId: String) =
    (TableQueries.notifications returning TableQueries.notifications.map(_.notificationsId)) +=
      NotificationModel(None, userId, date, false, typeId)

  private def createFixture(): NotificationFixture = {
    val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(12)
    val ownerId = s"notify-owner-$suffix"
    val actorId = s"notify-actor-$suffix"
    val now = System.currentTimeMillis()
    val action = for {
      _ <- TableQueries.users ++= Seq(
        User(ownerId, s"o$suffix", s"owner-$suffix", "test", "user", Some(now)),
        User(actorId, s"a$suffix", s"actor-$suffix", "test", "user", Some(now))
      )
      wordId <- (TableQueries.words returning TableQueries.words.map(_.wordId)) += Word(None, s"通知語-$suffix")
      tagId <- (TableQueries.tags returning TableQueries.tags.map(_.tagId)) += TagModel(None, s"通知タグ-$suffix")
      _ <- TableQueries.wordTags += WordTagModel(wordId, tagId, Some(ownerId), now, "つうち")
      meaningId <- (TableQueries.meanings returning TableQueries.meanings.map(_.meaningId)) += MeaningModel(
        None,
        wordId,
        tagId,
        Some(ownerId),
        now,
        "通知対象の語釈",
        false,
        0,
        0,
        0,
        false,
        now
      )
      exampleId <- (TableQueries.examples returning TableQueries.examples.map(_.exampleId)) += ExampleModel(
        None,
        wordId,
        tagId,
        Some(ownerId),
        now,
        "通知対象の用例",
        false,
        0,
        0,
        0,
        false,
        now
      )
      threadId <- (TableQueries.threads returning TableQueries.threads.map(_.threadId)) += ThreadModel(
        None,
        wordId,
        tagId,
        Some(ownerId),
        "3",
        now,
        "通知対象のスレッド",
        0,
        false,
        now
      )
      commentId <- (TableQueries.comments returning TableQueries.comments.map(_.commentId)) += CommentModel(
        None,
        threadId,
        Some(actorId),
        None,
        now,
        "通知対象のコメント",
        0,
        0,
        false,
        now
      )
    } yield NotificationFixture(ownerId, actorId, wordId, tagId, meaningId, exampleId, threadId, commentId)

    Await.result(database.run(action.transactionally), 5.seconds)
  }

  private def cleanupFixture(fixture: NotificationFixture): Unit = {
    val userIds = Set(fixture.ownerId, fixture.actorId)
    val action = for {
      _ <- TableQueries.notifications.filter(_.userId.inSet(userIds)).delete
      _ <- TableQueries.reactionGood.filter(_.userId.inSet(userIds)).delete
      _ <- TableQueries.reactionHelpful.filter(_.userId.inSet(userIds)).delete
      _ <- TableQueries.reactionFunny.filter(_.userId.inSet(userIds)).delete
      _ <- TableQueries.commentReactions.filter(_.userId.inSet(userIds)).delete
      _ <- TableQueries.comments.filter(_.commentId === fixture.commentId).delete
      _ <- TableQueries.threads.filter(_.threadId === fixture.threadId).delete
      _ <- TableQueries.meanings.filter(_.meaningId === fixture.meaningId).delete
      _ <- TableQueries.examples.filter(_.exampleId === fixture.exampleId).delete
      _ <- TableQueries.wordTags.filter(w => w.wordId === fixture.wordId && w.tagId === fixture.tagId).delete
      _ <- TableQueries.words.filter(_.wordId === fixture.wordId).delete
      _ <- TableQueries.tags.filter(_.tagId === fixture.tagId).delete
      _ <- TableQueries.users.filter(_.userId.inSet(userIds)).delete
    } yield ()
    Await.result(database.run(action.transactionally), 5.seconds)
  }
}
