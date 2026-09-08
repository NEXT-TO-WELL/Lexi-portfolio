package repositories
import models.db.{TagModel, Word}
import models.db.User
import models.db.TableQueries

import models.*
import models.db.TableQueries.meanings
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class MeaningModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[MeaningModel]] = {
    db.run(meanings.result)
  }

  def findByUserId(userId: String): Future[Seq[(MeaningModel, Option[User])]] = {
    val query = meanings
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .filter { case (m, _) => m.userId === userId }

    db.run(query.result)
  }

  def countByUserId(userId: String): Future[Int] = {
    db.run(
      meanings
        .filter(_.userId === userId)
        .filter(!_.isDeleted) // ★ ここを追加：削除されていないものだけ数える
        .length
        .result
    )
  }

  /** wordIdとtagIdに対応する意味を取得する */
  def findByWordTagId(wordId: Long, tagId: Long): Future[Seq[(MeaningModel, Option[User])]] = {
    val query = meanings
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .filter { case (w, _) => w.wordId === wordId }
      .filter { case (m, _) => m.tagId === tagId }
      .filter { case (m, _) => !m.isDeleted }

    db.run(query.result)
  }

  /** データを新規登録する */
  def create(meaning: MeaningModel): Future[Long] = {
    db.run(meanings returning meanings.map(_.meaningId) += meaning)
  }

  /** データ情報を更新する */
  def update(meaning: MeaningModel): Future[Int] = {
    db.run(meanings.filter(_.meaningId === meaning.meaningId).update(meaning))
  }

  /** データを削除する */
  def delete(meaningId: Option[Long]): Future[Int] = {
    db.run(meanings.filter(_.meaningId === meaningId).delete)
  }

  // app/repositories/MeaningModelRepository.scala

  def updateReactionCount(meaningId: Long, reactionType: String, delta: Int): Future[Int] = {
    // SQLレベルでアトミックに更新（競合状態を回避）
    val query = meanings.filter(_.meaningId === meaningId)

    val updateAction = reactionType match {
      case "good" =>
        query.map(m => m.meaningGoodCount).result.headOption.flatMap {
          case Some(count) =>
            val newCount = Math.max(0, count + delta)
            query.map(_.meaningGoodCount).update(newCount).map(_ => newCount)
          case None => DBIO.successful(0)
        }
      case "funny" =>
        query.map(m => m.meaningFunnyCount).result.headOption.flatMap {
          case Some(count) =>
            val newCount = Math.max(0, count + delta)
            query.map(_.meaningFunnyCount).update(newCount).map(_ => newCount)
          case None => DBIO.successful(0)
        }
      case "helpful" =>
        query.map(m => m.meaningHelpfulCount).result.headOption.flatMap {
          case Some(count) =>
            val newCount = Math.max(0, count + delta)
            query.map(_.meaningHelpfulCount).update(newCount).map(_ => newCount)
          case None => DBIO.successful(0)
        }
      case _ => DBIO.successful(0)
    }

    db.run(updateAction.transactionally)
  }

  def findByUserIdWithWordTag(userId: String): Future[Seq[(MeaningModel, Option[User], Word, TagModel)]] = {
    val query = for {
      (((m, u), w), t) <- TableQueries.meanings
        .filter(m => m.userId === userId && !m.isDeleted) joinLeft
        TableQueries.users on (_.userId === _.userId) join
        TableQueries.words on (_._1.wordId === _.wordId) join
        TableQueries.tags on (_._1._1.tagId === _.tagId)
    } yield (m, u, w, t)

    db.run(query.result)
  }

  /** IDで単一のMeaningを取得 */
  def findById(meaningId: Long): Future[Option[MeaningModel]] = {
    db.run(meanings.filter(_.meaningId === meaningId).result.headOption)
  }

  /** 論理削除を行う */
  def logicalDelete(meaningId: Long): Future[Int] = {
    val now = System.currentTimeMillis()
    db.run(meanings.filter(_.meaningId === meaningId).result.headOption).flatMap {
      case Some(m) =>
        val updated = m.copy(isDeleted = true, lastModified = now)
        update(updated)
      case None =>
        Future.successful(0)
    }
  }

  /** ページネーション対応：カーソルベースで語釈を取得 */
  def findByWordTagIdPaginated(
      wordId: Long,
      tagId: Long,
      lastId: Option[Long],
      limit: Int = 10
  ): Future[Seq[(MeaningModel, Option[User])]] = {
    val query = meanings
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .filter { case (m, _) => m.wordId === wordId && m.tagId === tagId && !m.isDeleted }
      .filterOpt(lastId) { case ((m, _), id) => m.meaningId < id }
      .sortBy { case (m, _) => m.meaningId.desc }
      .take(limit)

    db.run(query.result)
  }
}
