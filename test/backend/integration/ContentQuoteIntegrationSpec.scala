package integration

import models.MeaningModel
import models.db.{TableQueries, TagModel, ThreadModel, User, Word, WordTagModel}
import play.api.db.slick.DatabaseConfigProvider
import repositories.{CommunityPostRepository, ContentQuoteRepository, InvalidQuoteException}
import shared.api.{ContentQuoteReference, ContentQuoteTypes}
import slick.jdbc.MySQLProfile

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class ContentQuoteIntegrationSpec extends DatabaseIntegrationSpec {
  private val database = app.injector.instanceOf[DatabaseConfigProvider].get[MySQLProfile].db
  private val communityPostRepository = app.injector.instanceOf[CommunityPostRepository]
  private val contentQuoteRepository = app.injector.instanceOf[ContentQuoteRepository]
  import slick.jdbc.MySQLProfile.api.*

  "ContentQuoteRepository" should {
    "save, resolve, and audit a live quote" in {
      val fixture = createQuoteFixture()
      try {
        val now = System.currentTimeMillis()
        val created = Await.result(
          communityPostRepository.createThread(
            ThreadModel(None, fixture.wordId, fixture.tagId, Some(fixture.userId), "3", now, "引用テスト", 0, false, now),
            Some(ContentQuoteReference(ContentQuoteTypes.Meaning, fixture.meaningId)),
            fixture.userId
          ),
          5.seconds
        )
        val threadId = created.thread.threadId.value

        val active = Await
          .result(
            contentQuoteRepository.findForTarget(ContentQuoteTypes.Thread, threadId),
            5.seconds
          )
          .value
        active.isSourceDeleted mustBe false
        active.contentText mustBe Some("引用対象の語釈")

        Await.result(
          database.run(TableQueries.meanings.filter(_.meaningId === fixture.meaningId).map(_.isDeleted).update(true)),
          5.seconds
        )
        val deletedSource = Await
          .result(
            contentQuoteRepository.findForTarget(ContentQuoteTypes.Thread, threadId),
            5.seconds
          )
          .value
        deletedSource.isSourceDeleted mustBe true
        deletedSource.contentText mustBe None
        deletedSource.userName mustBe None

        Await.result(communityPostRepository.logicalDeleteThread(threadId, fixture.userId, now + 1), 5.seconds)
        val quote = Await.result(
          database.run(TableQueries.contentQuotes.filter(_.targetContentId === threadId).result.head),
          5.seconds
        )
        quote.isDeleted mustBe true
        quote.deletedByUserId mustBe Some(fixture.userId)
        quote.deletedAt mustBe defined
      } finally cleanupQuoteFixture(fixture)
    }

    "roll back a thread when its quote source is invalid" in {
      val fixture = createQuoteFixture()
      try {
        val before = Await.result(database.run(TableQueries.threads.length.result), 5.seconds)
        val now = System.currentTimeMillis()
        val failure = intercept[InvalidQuoteException] {
          Await.result(
            communityPostRepository.createThread(
              ThreadModel(None, fixture.wordId, fixture.tagId, Some(fixture.userId), "3", now, "失敗する投稿", 0, false, now),
              Some(ContentQuoteReference(ContentQuoteTypes.Meaning, Long.MaxValue)),
              fixture.userId
            ),
            5.seconds
          )
        }
        failure.reason must include("引用元")
        Await.result(database.run(TableQueries.threads.length.result), 5.seconds) mustBe before
      } finally cleanupQuoteFixture(fixture)
    }
  }

  private final case class QuoteFixture(userId: String, wordId: Long, tagId: Long, meaningId: Long)

  private def createQuoteFixture(): QuoteFixture = {
    val suffix = java.util.UUID.randomUUID().toString.replace("-", "").take(12)
    val userId = s"quote-test-$suffix"
    val now = System.currentTimeMillis()
    val action = for {
      _ <- TableQueries.users += User(userId, suffix, s"quote-$suffix", "test", "user", Some(now))
      word <- (TableQueries.words returning TableQueries.words.map(_.wordId)) += Word(None, s"引用語-$suffix")
      tag <- (TableQueries.tags returning TableQueries.tags.map(_.tagId)) += TagModel(None, s"引用タグ-$suffix")
      _ <- TableQueries.wordTags += WordTagModel(word, tag, Some(userId), now, "いんよう")
      meaning <- (TableQueries.meanings returning TableQueries.meanings.map(_.meaningId)) += MeaningModel(
        None,
        word,
        tag,
        Some(userId),
        now,
        "引用対象の語釈",
        false,
        0,
        0,
        0,
        false,
        now
      )
    } yield QuoteFixture(userId, word, tag, meaning)
    Await.result(database.run(action.transactionally), 5.seconds)
  }

  private def cleanupQuoteFixture(fixture: QuoteFixture): Unit = {
    val threadIds =
      TableQueries.threads.filter(t => t.wordId === fixture.wordId && t.tagId === fixture.tagId).map(_.threadId)
    val action = for {
      ids <- threadIds.result
      _ <- TableQueries.contentQuotes.filter(q => q.targetContentId.inSet(ids)).delete
      _ <- TableQueries.threadQuestions.filter(_.threadId.inSet(ids)).delete
      _ <- TableQueries.comments.filter(_.threadId.inSet(ids)).delete
      _ <- TableQueries.threads.filter(_.threadId.inSet(ids)).delete
      _ <- TableQueries.meanings.filter(_.meaningId === fixture.meaningId).delete
      _ <- TableQueries.wordTags.filter(wt => wt.wordId === fixture.wordId && wt.tagId === fixture.tagId).delete
      _ <- TableQueries.words.filter(_.wordId === fixture.wordId).delete
      _ <- TableQueries.tags.filter(_.tagId === fixture.tagId).delete
      _ <- TableQueries.users.filter(_.userId === fixture.userId).delete
    } yield ()
    Await.result(database.run(action.transactionally), 5.seconds)
  }
}
