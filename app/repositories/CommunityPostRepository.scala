package repositories

import models.CommentModel
import models.db.{ContentQuoteModel, ContentQuotesTable, TableQueries, ThreadModel, ThreadQuestionModel}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import shared.api.{ContentQuoteReference, ContentQuoteTypes}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

final case class InvalidQuoteException(reason: String) extends RuntimeException(reason)
final case class InvalidCommentTargetException(reason: String) extends RuntimeException(reason)

@Singleton
class CommunityPostRepository @Inject() (
    protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  final case class CreatedThread(thread: ThreadModel, question: Option[ThreadQuestionModel])

  def createThread(
      thread: ThreadModel,
      quote: Option[ContentQuoteReference],
      actorUserId: String
  ): Future[CreatedThread] = {
    val insertThread = TableQueries.threads returning TableQueries.threads.map(_.threadId) into { (item, id) =>
      item.copy(threadId = Some(id))
    }

    val action = for {
      _ <- validateQuoteSource(quote, thread.wordId, thread.tagId)
      savedThread <- insertThread += thread
      threadId = savedThread.threadId.getOrElse(throw IllegalStateException("Thread ID was not generated"))
      question <-
        if (thread.threadTypeId == "2") {
          val value = ThreadQuestionModel(threadId, false, None, None)
          (TableQueries.threadQuestions += value).map(_ => Some(value))
        } else DBIO.successful(None)
      _ <- insertQuote(quote, ContentQuoteTypes.Thread, threadId, actorUserId, thread.threadCreateDate)
    } yield CreatedThread(savedThread, question)

    db.run(action.transactionally)
  }

  def createComment(
      comment: CommentModel,
      quote: Option[ContentQuoteReference],
      actorUserId: String
  ): Future[CommentModel] = {
    val insertComment = TableQueries.comments returning TableQueries.comments.map(_.commentId) into { (item, id) =>
      item.copy(commentId = Some(id))
    }

    val action = for {
      threadOpt <- TableQueries.threads
        .filter(t => t.threadId === comment.threadId && !t.isDeleted)
        .result
        .headOption
      thread <- threadOpt match {
        case Some(value) => DBIO.successful(value)
        case None        => DBIO.failed(InvalidCommentTargetException("スレッドが見つかりません"))
      }
      _ <- validateParentComment(comment.parentCommentId, comment.threadId)
      _ <- validateQuoteSource(quote, thread.wordId, thread.tagId)
      savedComment <- insertComment += comment
      commentId = savedComment.commentId.getOrElse(throw IllegalStateException("Comment ID was not generated"))
      _ <- TableQueries.threads
        .filter(_.threadId === comment.threadId)
        .map(_.threadCommentCount)
        .update(thread.threadCommentCount + 1)
      _ <- insertQuote(quote, ContentQuoteTypes.Comment, commentId, actorUserId, comment.commentDate)
    } yield savedComment

    db.run(action.transactionally)
  }

  def logicalDeleteThread(threadId: Long, actorUserId: String, now: Long): Future[Int] = {
    val activeQuotes = TableQueries.contentQuotes.filter(!_.isDeleted)
    val action = for {
      commentIds <- TableQueries.comments.filter(_.threadId === threadId).map(_.commentId).result
      updated <- TableQueries.threads
        .filter(t => t.threadId === threadId && !t.isDeleted)
        .map(t => (t.isDeleted, t.lastModified))
        .update((true, now))
      _ <- TableQueries.comments
        .filter(c => c.threadId === threadId && !c.isDeleted)
        .map(c => (c.isDeleted, c.lastModified))
        .update((true, now))
      _ <- logicalDeleteQuotes(
        activeQuotes.filter(q => q.targetContentTypeId === ContentQuoteTypes.Thread && q.targetContentId === threadId),
        actorUserId,
        now
      )
      _ <-
        if (commentIds.nonEmpty) {
          logicalDeleteQuotes(
            activeQuotes
              .filter(q => q.targetContentTypeId === ContentQuoteTypes.Comment && q.targetContentId.inSet(commentIds)),
            actorUserId,
            now
          )
        } else DBIO.successful(0)
    } yield updated

    db.run(action.transactionally)
  }

  def logicalDeleteComment(commentId: Long, actorUserId: String, now: Long): Future[Int] = {
    val action = for {
      updated <- TableQueries.comments
        .filter(c => c.commentId === commentId && !c.isDeleted)
        .map(c => (c.isDeleted, c.lastModified))
        .update((true, now))
      _ <- logicalDeleteQuotes(
        TableQueries.contentQuotes.filter(q =>
          q.targetContentTypeId === ContentQuoteTypes.Comment && q.targetContentId === commentId && !q.isDeleted
        ),
        actorUserId,
        now
      )
    } yield updated

    db.run(action.transactionally)
  }

  private def validateParentComment(parentCommentId: Option[Long], threadId: Long): DBIO[Unit] =
    parentCommentId match {
      case None           => DBIO.successful(())
      case Some(parentId) =>
        TableQueries.comments
          .filter(c => c.commentId === parentId && c.threadId === threadId && !c.isDeleted)
          .exists
          .result
          .flatMap {
            case true  => DBIO.successful(())
            case false => DBIO.failed(InvalidCommentTargetException("返信先コメントが見つかりません"))
          }
    }

  private def validateQuoteSource(
      quote: Option[ContentQuoteReference],
      wordId: Long,
      tagId: Long
  ): DBIO[Unit] =
    quote match {
      case None                                        => DBIO.successful(())
      case Some(reference) if reference.contentId <= 0 =>
        DBIO.failed(InvalidQuoteException("引用元が正しくありません"))
      case Some(reference) if reference.contentTypeId == ContentQuoteTypes.Meaning =>
        TableQueries.meanings
          .filter(m => m.meaningId === reference.contentId && m.wordId === wordId && m.tagId === tagId && !m.isDeleted)
          .forUpdate
          .result
          .headOption
          .flatMap(result => requireValidQuote(result.isDefined))
      case Some(reference) if reference.contentTypeId == ContentQuoteTypes.Example =>
        TableQueries.examples
          .filter(e => e.exampleId === reference.contentId && e.wordId === wordId && e.tagId === tagId && !e.isDeleted)
          .forUpdate
          .result
          .headOption
          .flatMap(result => requireValidQuote(result.isDefined))
      case Some(_) =>
        DBIO.failed(InvalidQuoteException("引用できるのは語釈または用例だけです"))
    }

  private def requireValidQuote(exists: Boolean): DBIO[Unit] =
    if (exists) DBIO.successful(())
    else DBIO.failed(InvalidQuoteException("引用元が見つからないか、引用できない状態です"))

  private def insertQuote(
      quote: Option[ContentQuoteReference],
      targetContentTypeId: String,
      targetContentId: Long,
      actorUserId: String,
      now: Long
  ): DBIO[Int] =
    quote match {
      case None            => DBIO.successful(0)
      case Some(reference) =>
        TableQueries.contentQuotes += ContentQuoteModel(
          quoteId = None,
          sourceContentTypeId = reference.contentTypeId,
          sourceContentId = reference.contentId,
          targetContentTypeId = targetContentTypeId,
          targetContentId = targetContentId,
          createdByUserId = actorUserId,
          createdAt = now,
          lastModified = now,
          isDeleted = false,
          deletedByUserId = None,
          deletedAt = None
        )
    }

  private def logicalDeleteQuotes(
      query: Query[ContentQuotesTable, ContentQuoteModel, Seq],
      actorUserId: String,
      now: Long
  ): DBIO[Int] =
    query
      .map(q => (q.isDeleted, q.deletedByUserId, q.deletedAt, q.lastModified))
      .update((true, Some(actorUserId), Some(now), now))
}
