package repositories
import models.db.SearchHistoryModel

import models.db.TableQueries.searchHistories
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class SearchHistoryModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[SearchHistoryModel]] = {
    db.run(searchHistories.result)
  }

  /** IDでデータを1つ取得する */
  def findById(searchHistoryId: Option[Long]): Future[Option[SearchHistoryModel]] = {
    db.run(searchHistories.filter(_.searchHistoryId === searchHistoryId).result.headOption)
  }

  /** データを新規登録する */
  def create(hiddenUser: SearchHistoryModel): Future[Int] = {
    db.run(searchHistories += hiddenUser)
  }

  /** データ情報を更新する */
  def update(hiddenUser: SearchHistoryModel): Future[Int] = {
    db.run(searchHistories.filter(_.searchHistoryId === hiddenUser.searchHistoryId).update(hiddenUser))
  }

  /** データを削除する */
  def delete(searchHistoryId: Option[Long]): Future[Int] = {
    db.run(searchHistories.filter(_.searchHistoryId === searchHistoryId).delete)
  }
}
