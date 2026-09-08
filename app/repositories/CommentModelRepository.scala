package repositories
import models.db.User
import models.db.TableQueries

import models.*
import models.db.TableQueries.{comments, users}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class CommentModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[CommentModel]] = {
    db.run(comments.result)
  }

  /** IDでデータを1つ取得する */
  def findById(commentId: Option[Long]): Future[Option[CommentModel]] = {
    db.run(comments.filter(_.commentId === commentId).result.headOption)
  }

  // CommentRepository内に追加
  def findByThreadId(threadId: Long): Future[Seq[(CommentModel, String)]] = {
    // ユーザーテーブルとの結合（UserTableの定義がある前提）
    // userIdがOption[String]のため、leftJoinまたはInnerJoinを適切に使い分けます
    val query = for {
      c <- comments.filter(_.threadId === threadId).filter(!_.isDeleted)
      u <- users if c.userId === u.userId // UserTableを結合
    } yield (c, u.userName)

    db.run(query.result)
  }

  // app/repositories/CommentModelRepository.scala

  // コメントのカウントを更新し、最新値を返す
  def updateReactionCount(commentId: Long, reactionType: String, delta: Int): Future[Int] = {
    db.run(comments.filter(_.commentId === commentId).result.headOption).flatMap {
      case Some(c) =>
        val (updatedComment, newCount) = reactionType match {
          case "good" =>
            val count = Math.max(0, c.commentGoodCount + delta)
            (c.copy(commentGoodCount = count), count)
          case "bad" =>
            val count = Math.max(0, c.commentBadCount + delta)
            (c.copy(commentBadCount = count), count)
          case _ => (c, 0)
        }
        db.run(comments.filter(_.commentId === commentId).update(updatedComment)).map(_ => newCount)
      case None => Future.successful(0)
    }
  }

  def countGroupedByThreadIds(threadIds: Seq[Long]): Future[Map[Long, Int]] = {
    val query = comments
      .filter(_.threadId inSet threadIds)
      .groupBy(_.threadId)
      .map { case (id, group) => (id, group.length) }

    db.run(query.result).map(_.toMap)
  }

  // 既存の updateReactionCount を拡張するか、新しいメソッドを追加
  // 既存の updateReactionCount を参考に、両方のカウントを返すメソッドを追加
  def updateAndGetCounts(commentId: Long, reactionType: String, delta: Int): Future[(Int, Int)] = {
    db.run(comments.filter(_.commentId === commentId).result.headOption).flatMap {
      case Some(c) =>
        val updatedComment = reactionType match {
          case "good" => c.copy(commentGoodCount = Math.max(0, c.commentGoodCount + delta))
          case "bad"  => c.copy(commentBadCount = Math.max(0, c.commentBadCount + delta))
          case _      => c
        }
        db.run(comments.filter(_.commentId === commentId).update(updatedComment))
          .map(_ => (updatedComment.commentGoodCount, updatedComment.commentBadCount))
      case None => Future.successful((0, 0))
    }
  }

  /** ユーザーIDでデータ取得 */
  def findByUserId(userId: String): Future[Seq[(CommentModel, User)]] = {
    val query = for {
      // 自分の投稿したコメントを抽出し、かつ削除されていないものに絞り込む
      c <- comments if c.userId === userId && !c.isDeleted

      // スレッドと結合して、削除されていないスレッドのコメントのみ取得
      t <- TableQueries.threads if c.threadId === t.threadId && !t.isDeleted

      // usersテーブルと userId で結合
      u <- TableQueries.users if c.userId === u.userId
    } yield (c, u)

    db.run(query.result)
  }

  /** データを新規登録する */
  def create(commentModel: CommentModel): Future[Int] = {
    db.run(comments += commentModel)
  }

  def insert(comment: CommentModel): Future[CommentModel] = {
    // 挿入して生成されたIDをモデルにセットして返す
    val action = comments returning comments.map(_.commentId) into ((item, id) => item.copy(commentId = Some(id)))
    db.run(action += comment)
  }

  def findByThreadIdWithUser(threadId: Long): Future[Seq[(CommentModel, String)]] = {
    val query = for {
      c <- comments if c.threadId === threadId && !c.isDeleted
      u <- users if c.userId === u.userId
    } yield (c, u.userName)

    db.run(query.result)
  }

  /** データ情報を更新する */
  def update(commentModel: CommentModel): Future[Int] = {
    db.run(comments.filter(_.commentId === commentModel.commentId).update(commentModel))
  }

  /** データを削除する */
  def delete(commentId: Option[Long]): Future[Int] = {
    db.run(comments.filter(_.commentId === commentId).delete)
  }

  /** コメントを論理削除する */
  def logicalDelete(commentId: Long): Future[Int] = {
    db.run(
      comments
        .filter(_.commentId === commentId)
        .map(_.isDeleted)
        .update(true)
    )
  }

  /** スレッドIDに属する全てのコメントを論理削除する */
  def logicalDeleteByThreadId(threadId: Long): Future[Int] = {
    db.run(
      comments
        .filter(_.threadId === threadId)
        .map(_.isDeleted)
        .update(true)
    )
  }

  def countByUserId(userId: String): Future[Int] = {
    val query = for {
      c <- comments if c.userId === userId && !c.isDeleted
      t <- TableQueries.threads if c.threadId === t.threadId && !t.isDeleted
    } yield c

    db.run(query.length.result)
  }

  /** ページネーション対応：カーソルベースでコメントを取得 */
  def findByThreadIdWithUserPaginated(
      threadId: Long,
      lastId: Option[Long],
      limit: Int = 10
  ): Future[Seq[(CommentModel, String)]] = {
    val query = comments
      .join(users)
      .on(_.userId === _.userId)
      .filter { case (c, _) => c.threadId === threadId && !c.isDeleted }
      .filterOpt(lastId) { case ((c, _), id) => c.commentId < id }
      .sortBy { case (c, _) => c.commentId.desc }
      .take(limit)
      .map { case (c, u) => (c, u.userName) }

    db.run(query.result)
  }
}
