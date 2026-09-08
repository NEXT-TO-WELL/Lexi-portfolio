package repositories
import models.db.{TagModel, Word}
import models.db.User
import models.db.TableQueries

import models.*
import models.db.TableQueries.examples
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ExampleModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ExampleModel]] = {
    db.run(examples.result)
  }

  def findByUserId(userId: String): Future[Seq[(ExampleModel, Option[User])]] = {
    val query = examples
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .filter { case (w, _) => w.userId === userId }

    db.run(query.result)
  }

  def countByUserId(userId: String): Future[Int] = {
    db.run(
      examples
        .filter(_.userId === userId)
        .filter(!_.isDeleted) // ★ ここを追加：論理削除されていないデータのみカウント
        .length
        .result
    )
  }

  /** IDでデータを1つ取得する */
  def findByWordTagId(wordId: Long, tagId: Long): Future[Seq[(ExampleModel, Option[User])]] = {
    val query = examples
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .filter { case (w, _) => w.wordId === wordId }
      .filter { case (e, _) => e.tagId === tagId }
      .filter { case (e, _) => !e.isDeleted }

    db.run(query.result)
  }

  // app/repositories/ExampleModelRepository.scala

  def updateReactionCount(exampleId: Long, reactionType: String, delta: Int): Future[Int] = {
    // SQLレベルでアトミックに更新（競合状態を回避）
    val query = examples.filter(_.exampleId === exampleId)

    val updateAction = reactionType match {
      case "good" =>
        query.map(e => e.exampleGoodCount).result.headOption.flatMap {
          case Some(count) =>
            val newCount = Math.max(0, count + delta)
            query.map(_.exampleGoodCount).update(newCount).map(_ => newCount)
          case None => DBIO.successful(0)
        }
      case "funny" =>
        query.map(e => e.exampleFunnyCount).result.headOption.flatMap {
          case Some(count) =>
            val newCount = Math.max(0, count + delta)
            query.map(_.exampleFunnyCount).update(newCount).map(_ => newCount)
          case None => DBIO.successful(0)
        }
      case "helpful" =>
        query.map(e => e.exampleHelpfulCount).result.headOption.flatMap {
          case Some(count) =>
            val newCount = Math.max(0, count + delta)
            query.map(_.exampleHelpfulCount).update(newCount).map(_ => newCount)
          case None => DBIO.successful(0)
        }
      case _ => DBIO.successful(0)
    }

    db.run(updateAction.transactionally)
  }

  /** データを新規登録する */
  def create(example: ExampleModel): Future[Long] = {
    db.run((examples returning examples.map(_.exampleId)) += example)
  }

  /** データ情報を更新する */
  def update(example: ExampleModel): Future[Int] = {
    db.run(examples.filter(_.exampleId === example.exampleId).update(example))
  }

  /** データを削除する */
  def delete(exampleId: Option[Long]): Future[Int] = {
    db.run(examples.filter(_.exampleId === exampleId).delete)
  }

  def findByUserIdWithWordTag(userId: String): Future[Seq[(ExampleModel, Option[User], Word, TagModel)]] = {
    val query = for {
      (((e, u), w), t) <- TableQueries.examples
        .filter(e => e.userId === userId && !e.isDeleted) joinLeft
        TableQueries.users on (_.userId === _.userId) join
        TableQueries.words on (_._1.wordId === _.wordId) join
        TableQueries.tags on (_._1._1.tagId === _.tagId)
    } yield (e, u, w, t)

    db.run(query.result)
  }

  /** IDで単一のExampleを取得 */
  def findById(exampleId: Long): Future[Option[ExampleModel]] = {
    db.run(examples.filter(_.exampleId === exampleId).result.headOption)
  }

  /** 論理削除を行う */
  def logicalDelete(exampleId: Long): Future[Int] = {
    val now = System.currentTimeMillis()
    db.run(examples.filter(_.exampleId === exampleId).result.headOption).flatMap {
      case Some(e) =>
        val updated = e.copy(isDeleted = true, lastModified = now)
        update(updated)
      case None =>
        Future.successful(0)
    }
  }

  /** ページネーション対応：カーソルベースで用例を取得 */
  def findByWordTagIdPaginated(
      wordId: Long,
      tagId: Long,
      lastId: Option[Long],
      limit: Int = 20
  ): Future[Seq[(ExampleModel, Option[User])]] = {
    val query = examples
      .joinLeft(TableQueries.users)
      .on(_.userId === _.userId)
      .filter { case (e, _) => e.wordId === wordId && e.tagId === tagId && !e.isDeleted }
      .filterOpt(lastId) { case ((e, _), id) => e.exampleId < id }
      .sortBy { case (e, _) => e.exampleId.desc }
      .take(limit)

    db.run(query.result)
  }
}
