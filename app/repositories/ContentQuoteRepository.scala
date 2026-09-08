package repositories

import models.db.{ContentQuoteModel, TableQueries}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import shared.api.{ContentQuoteTypes, ContentQuoteViewValue}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContentQuoteRepository @Inject() (
    protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  def listAll(): Future[Seq[ContentQuoteModel]] =
    db.run(TableQueries.contentQuotes.result)

  def findForThreadIds(threadIds: Seq[Long]): Future[Map[Long, ContentQuoteViewValue]] =
    findForTargets(ContentQuoteTypes.Thread, threadIds)

  def findForCommentIds(commentIds: Seq[Long]): Future[Map[Long, ContentQuoteViewValue]] =
    findForTargets(ContentQuoteTypes.Comment, commentIds)

  def findForTarget(targetContentTypeId: String, targetContentId: Long): Future[Option[ContentQuoteViewValue]] =
    findForTargets(targetContentTypeId, Seq(targetContentId)).map(_.get(targetContentId))

  private def findForTargets(
      targetContentTypeId: String,
      targetContentIds: Seq[Long]
  ): Future[Map[Long, ContentQuoteViewValue]] = {
    if (targetContentIds.isEmpty) return Future.successful(Map.empty)

    val quoteQuery = TableQueries.contentQuotes.filter(q =>
      q.targetContentTypeId === targetContentTypeId && q.targetContentId.inSet(targetContentIds) && !q.isDeleted
    )

    db.run(quoteQuery.result).flatMap { quotes =>
      val meaningIds = quotes.filter(_.sourceContentTypeId == ContentQuoteTypes.Meaning).map(_.sourceContentId)
      val exampleIds = quotes.filter(_.sourceContentTypeId == ContentQuoteTypes.Example).map(_.sourceContentId)

      for {
        meaningViews <- loadMeaningViews(meaningIds)
        exampleViews <- loadExampleViews(exampleIds)
      } yield quotes.map { quote =>
        val view = quote.sourceContentTypeId match {
          case ContentQuoteTypes.Meaning => meaningViews.getOrElse(quote.sourceContentId, deletedView(quote))
          case ContentQuoteTypes.Example => exampleViews.getOrElse(quote.sourceContentId, deletedView(quote))
          case _                         => deletedView(quote)
        }
        quote.targetContentId -> view
      }.toMap
    }
  }

  private def loadMeaningViews(ids: Seq[Long]): Future[Map[Long, ContentQuoteViewValue]] = {
    if (ids.isEmpty) return Future.successful(Map.empty)

    val query = TableQueries.meanings
      .filter(_.meaningId.inSet(ids))
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .join(TableQueries.words)
      .on(_._1.wordId === _.wordId)
      .join(TableQueries.tags)
      .on(_._1._1.tagId === _.tagId)
      .map { case (((meaning, user), word), tag) => (meaning, user, word, tag) }

    db.run(query.result)
      .map(_.map { case (meaning, user, word, tag) =>
        val id = meaning.meaningId.getOrElse(0L)
        id -> (if (meaning.isDeleted) {
                 deletedView(ContentQuoteTypes.Meaning, id)
               } else {
                 ContentQuoteViewValue(
                   sourceContentTypeId = ContentQuoteTypes.Meaning,
                   sourceContentId = id,
                   isSourceDeleted = false,
                   contentText = Some(meaning.meaningText),
                   userName = Some(user.map(_.userName).getOrElse("削除済みユーザー")),
                   displayId = user.map(_.displayId),
                   wordId = Some(meaning.wordId),
                   tagId = Some(meaning.tagId),
                   wordMain = Some(word.wordMain),
                   tagName = Some(tag.tagName)
                 )
               })
      }.toMap)
  }

  private def loadExampleViews(ids: Seq[Long]): Future[Map[Long, ContentQuoteViewValue]] = {
    if (ids.isEmpty) return Future.successful(Map.empty)

    val query = TableQueries.examples
      .filter(_.exampleId.inSet(ids))
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .join(TableQueries.words)
      .on(_._1.wordId === _.wordId)
      .join(TableQueries.tags)
      .on(_._1._1.tagId === _.tagId)
      .map { case (((example, user), word), tag) => (example, user, word, tag) }

    db.run(query.result)
      .map(_.map { case (example, user, word, tag) =>
        val id = example.exampleId.getOrElse(0L)
        id -> (if (example.isDeleted) {
                 deletedView(ContentQuoteTypes.Example, id)
               } else {
                 ContentQuoteViewValue(
                   sourceContentTypeId = ContentQuoteTypes.Example,
                   sourceContentId = id,
                   isSourceDeleted = false,
                   contentText = Some(example.exampleText),
                   userName = Some(user.map(_.userName).getOrElse("削除済みユーザー")),
                   displayId = user.map(_.displayId),
                   wordId = Some(example.wordId),
                   tagId = Some(example.tagId),
                   wordMain = Some(word.wordMain),
                   tagName = Some(tag.tagName)
                 )
               })
      }.toMap)
  }

  private def deletedView(quote: ContentQuoteModel): ContentQuoteViewValue =
    deletedView(quote.sourceContentTypeId, quote.sourceContentId)

  private def deletedView(sourceContentTypeId: String, sourceContentId: Long): ContentQuoteViewValue =
    ContentQuoteViewValue(
      sourceContentTypeId = sourceContentTypeId,
      sourceContentId = sourceContentId,
      isSourceDeleted = true,
      contentText = None,
      userName = None,
      displayId = None,
      wordId = None,
      tagId = None,
      wordMain = None,
      tagName = None
    )
}
