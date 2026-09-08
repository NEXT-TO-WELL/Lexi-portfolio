package repositories
import models.db.FollowModel
import models.db.{TagModel, Word}
import models.db.TableQueries

import models.*
import models.db.TableQueries.follows
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class FollowModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[FollowModel]] = {
    db.run(follows.result)
  }

  // ワードフォロー一覧 (wordId is NOT NULL)
  def findFollowedWords(userId: String): Future[Seq[(FollowModel, Word, TagModel)]] = {
    val query = follows
      .filter(f => f.userId === userId && f.wordId.isDefined)
      .join(TableQueries.words)
      .on(_.wordId === _.wordId)
      .join(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .map { case ((f, w), t) => (f, w, t) }
    db.run(query.result)
  }

  // タグフォロー一覧 (wordId is NULL)
  def findFollowedTags(userId: String): Future[Seq[(FollowModel, TagModel)]] = {
    val query = follows
      .filter(f => f.userId === userId && f.wordId.isEmpty)
      .join(TableQueries.tags)
      .on(_.tagId === _.tagId)
    db.run(query.result)
  }

  /** 既存のフォロー関係を探す（userId, tagId, wordId の組み合わせ */
  def findExisting(userId: String, tagId: Long, wordId: Option[Long]): Future[Option[FollowModel]] = {
    db.run(
      follows
        .filter(f => f.userId === userId && f.tagId === tagId && f.wordId === wordId)
        .result
        .headOption
    )
  }

  /** IDでデータを1つ取得する */
  def findById(followId: Option[Long]): Future[Option[FollowModel]] = {
    db.run(follows.filter(_.followId === followId).result.headOption)
  }

  /** データを新規登録する */
  def create(follow: FollowModel): Future[FollowModel] = {
    // 挿入して生成された ID を含めたモデルを返す
    val insertQuery = (follows returning follows.map(_.followId)
      into ((item, id) => item.copy(followId = Some(id))))
    db.run(insertQuery += follow)
  }

  /** データ情報を更新する */
  def update(hiddenUser: FollowModel): Future[Int] = {
    db.run(follows.filter(_.followId === hiddenUser.followId).update(hiddenUser))
  }

  /** データを削除する */
  def deleteById(followId: Long): Future[Int] = {
    db.run(follows.filter(_.followId === followId).delete)
  }

  def deleteByIdAndUser(followId: Long, userId: String): Future[Int] = {
    db.run(follows.filter(f => f.followId === followId && f.userId === userId).delete)
  }

  def deleteByPair(userId: String, tagId: Long, wordId: Option[Long]): Future[Int] = {
    db.run(
      follows
        .filter(f => f.userId === userId && f.tagId === tagId && f.wordId === wordId)
        .delete
    )
  }
}
