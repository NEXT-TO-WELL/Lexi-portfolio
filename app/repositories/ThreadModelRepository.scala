package repositories
import models.db.User
import models.db.{ThreadModel, ThreadQuestionModel}
import models.db.TableQueries

import models.*
import models.db.TableQueries.threads
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート
import play.api.Logger

@Singleton
class ThreadModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*
  private val logger = Logger(getClass)

  /** 全データを取得する */
  def listAll(): Future[Seq[ThreadModel]] = {
    db.run(threads.result)
  }

  /** 全データを取得する（ユーザーとスレッドタイプ含む） */
  def listAllWithDetails(): Future[Seq[(ThreadModel, ThreadType, Option[User])]] = {
    val query = threads
      .join(TableQueries.threadTypes)
      .on(_.threadTypeId === _.threadTypeId)
      .joinLeft(TableQueries.users)
      .on(_._1.userId === _.userId)
      .filter { case ((t, _), _) => !t.isDeleted }
      .map { case ((t, tt), u) => (t, tt, u) }

    db.run(query.result)
  }

  def updateCommentCount(threadId: Long, delta: Int): Future[Int] = {
    db.run(threads.filter(_.threadId === threadId).result.headOption).flatMap {
      case Some(t) =>
        val newCount = Math.max(0, t.threadCommentCount + delta)
        db.run(threads.filter(_.threadId === threadId).map(_.threadCommentCount).update(newCount))
      case None => Future.successful(0)
    }
  }

  def incrementCommentCount(threadId: Long): Future[Int] = {
    // 1. 対象のスレッドを検索
    val query = threads.filter(_.threadId === threadId)

    // 2. 現在のカウントを取得して +1 するアクションを組み立てる
    val action = query.result.headOption.flatMap {
      case Some(thread) =>
        // 現在の値 + 1 で更新
        query.map(_.threadCommentCount).update(thread.threadCommentCount + 1)
      case None =>
        // スレッドが見つからない場合は何もしない
        DBIO.successful(0)
    }

    // 3. 実行
    db.run(action)
  }

  def findActiveById(threadId: Long): Future[Option[ThreadModel]] = {
    db.run(threads.filter(t => t.threadId === threadId && !t.isDeleted).result.headOption)
  }

  /** IDでデータを1つ取得する */
  // ThreadRepository.scala
  def findByWordAndTag(
      wordId: Long,
      tagId: Long
  ): Future[Seq[(ThreadModel, Option[ThreadQuestionModel], ThreadType, Option[User])]] = {
    val query = TableQueries.threads
      .joinLeft(TableQueries.threadQuestions)
      .on(_.threadId === _.threadId)
      .join(TableQueries.threadTypes)
      .on(_._1.threadTypeId === _.threadTypeId)
      .joinLeft(TableQueries.users)
      .on(_._1._1.userId === _.userId) // ★Userを結合
      .filter { case (((t, _), _), _) =>
        t.wordId === wordId && t.tagId === tagId && !t.isDeleted
      }
      .map { case (((t, tq), tt), u) => (t, tq, tt, u) } // ★戻り値に u を追加

    db.run(query.result)
  }

  /** ユーザーIDでデータ取得（質問ステータス、ユーザー情報を含む） */
  def findByUserId(
      userId: String
  ): Future[Seq[(ThreadModel, Option[ThreadQuestionModel], Option[User], ThreadType)]] = {
    val query = threads
      .joinLeft(TableQueries.threadQuestions)
      .on(_.threadId === _.threadId)
      .joinLeft(TableQueries.users)
      .on(_._1.userId === _.userId) // ユーザー情報を結合
      .join(TableQueries.threadTypes)
      .on(_._1._1.threadTypeId === _.threadTypeId)
      .filter { case (((base, _), _), _) => base.userId === userId && !base.isDeleted }
      .map { case (((base, tq), u), tt) => (base, tq, u, tt) }

    db.run(query.result)
  }

  def insert(thread: ThreadModel): Future[ThreadModel] = {
    // 挿入後、自動採番されたIDを取得してモデルを更新して返す
    val query = threads returning threads.map(_.threadId) into ((item, id) => item.copy(threadId = Some(id)))
    db.run(query += thread)
  }

  /** データを新規登録する */
  def create(threadModel: ThreadModel): Future[Int] = {
    db.run(threads += threadModel)
  }

  /** データ情報を更新する */
  def update(threadModel: ThreadModel): Future[Int] = {
    db.run(threads.filter(_.threadId === threadModel.threadId).update(threadModel))
  }

  /** データを削除する */
  def delete(threadId: Option[Long]): Future[Int] = {
    db.run(threads.filter(_.threadId === threadId).delete)
  }

  def countByUserId(userId: String): Future[Int] = {
    db.run(
      threads
        .filter(_.userId === userId)
        .filter(!_.isDeleted) // ★ 論理削除されていないスレッドのみカウント
        .length
        .result
    )
  }

  /** IDで単一のThreadを取得 */
  def findById(threadId: Long): Future[Option[ThreadModel]] = {
    db.run(threads.filter(_.threadId === threadId).result.headOption)
  }

  /** 論理削除を行う */
  def logicalDelete(threadId: Long): Future[Int] = {
    val now = System.currentTimeMillis()
    db.run(threads.filter(_.threadId === threadId).result.headOption).flatMap {
      case Some(t) =>
        val updated = t.copy(isDeleted = true, lastModified = now)
        update(updated)
      case None =>
        Future.successful(0)
    }
  }

  /** ページネーション対応：カーソルベースでスレッドを取得 */
  def findByWordAndTagPaginated(
      wordId: Long,
      tagId: Long,
      lastId: Option[Long],
      limit: Int = 10
  ): Future[Seq[(ThreadModel, Option[ThreadQuestionModel], ThreadType, Option[User])]] = {
    val query = TableQueries.threads
      .joinLeft(TableQueries.threadQuestions)
      .on(_.threadId === _.threadId)
      .join(TableQueries.threadTypes)
      .on(_._1.threadTypeId === _.threadTypeId)
      .joinLeft(TableQueries.users)
      .on(_._1._1.userId === _.userId)
      .filter { case (((t, _), _), _) =>
        t.wordId === wordId && t.tagId === tagId && !t.isDeleted
      }
      .filterOpt(lastId) { case ((((t, _), _), _), id) => t.threadId < id }
      .sortBy { case (((t, _), _), _) => t.threadId.desc }
      .take(limit)
      .map { case (((t, tq), tt), u) => (t, tq, tt, u) }

    db.run(query.result)
  }
}
