package repositories

import models.*
import models.db.TableQueries.threadTypes
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ThreadTypeRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ThreadType]] = {
    db.run(threadTypes.result)
  }

  /** IDでデータを1つ取得する */
  def findById(threadTypeId: String): Future[Option[ThreadType]] = {
    db.run(threadTypes.filter(_.threadTypeId === threadTypeId).result.headOption)
  }

  /** データを新規登録する */
  def create(threadType: ThreadType): Future[Int] = {
    db.run(threadTypes += threadType)
  }

  /** データ情報を更新する */
  def update(threadType: ThreadType): Future[Int] = {
    db.run(threadTypes.filter(_.threadTypeId === threadType.threadTypeId).update(threadType))
  }

  /** データを削除する */
  def delete(threadTypeId: String): Future[Int] = {
    db.run(threadTypes.filter(_.threadTypeId === threadTypeId).delete)
  }
}
