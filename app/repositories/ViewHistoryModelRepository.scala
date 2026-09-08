package repositories
import models.db.ViewHistoryModel
import models.db.TableQueries

import models.*
import models.db.TableQueries.viewHistories
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ViewHistoryModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ViewHistoryModel]] = {
    db.run(viewHistories.result)
  }

  /** IDでデータを1つ取得する */
  def findById(userId: String): Future[Option[ViewHistoryModel]] = {
    db.run(viewHistories.filter(_.userId === userId).result.headOption)
  }

  def upsertViewHistory(userId: String, wordId: Long, tagId: Long): Future[Int] = {
    val now = System.currentTimeMillis()
    // insertOrUpdate を使う
    val action = TableQueries.viewHistories.insertOrUpdate(
      ViewHistoryModel(userId, wordId, tagId, now)
    )
    db.run(action)
  }

  def findByUserId(userId: String, limit: Int = 30): Future[Seq[ViewHistoryViewValue]] = {
    // 1. 各結合で引数を明示的に (a, b) => ... の形で書く
    val query = TableQueries.viewHistories
      .filter(_.userId === userId)
      .join(TableQueries.words)
      .on((vh, w) => vh.wordId === w.wordId)
      .join(TableQueries.tags)
      .on { case ((vh, w), t) => vh.tagId === t.tagId }
      // ここで wordTags を結合
      .join(TableQueries.wordTags)
      .on { case (((vh, w), t), wt) =>
        vh.wordId === wt.wordId && vh.tagId === wt.tagId
      }
      .sortBy { case (((vh, w), t), wt) => vh.viewDate.desc }
      .take(limit)
      .map { case (((vh, w), t), wt) =>
        (vh.wordId, vh.tagId, w.wordMain, t.tagName, wt.wordReading, vh.viewDate)
      }

    db.run(query.result).map { (rows: Seq[(Long, Long, String, String, String, Long)]) =>
      rows.map { case (wId, tId, wMain, tName, wRead, vDate) =>
        ViewHistoryViewValue(wId, tId, wMain, tName, wRead, vDate)
      }
    }
  }

  /** データを新規登録する */
  def create(viewHistory: ViewHistoryModel): Future[Int] = {
    db.run(viewHistories += viewHistory)
  }

  /** データ情報を更新する */
  def update(viewHistory: ViewHistoryModel): Future[Int] = {
    db.run(viewHistories.filter(_.userId === viewHistory.userId).update(viewHistory))
  }

  /** データを削除する */
  def delete(userId: String): Future[Int] = {
    db.run(viewHistories.filter(_.userId === userId).delete)
  }
}
