package repositories
import models.db.ContentType

import models.db.TableQueries.contentTypes
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ContentTypeRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ContentType]] = {
    db.run(contentTypes.result)
  }

  /** IDでデータを1つ取得する */
  def findById(contentTypeId: String): Future[Option[ContentType]] = {
    db.run(contentTypes.filter(_.contentTypeId === contentTypeId).result.headOption)
  }

  /** データを新規登録する */
  def create(contentType: ContentType): Future[Int] = {
    db.run(contentTypes += contentType)
  }

  /** データ情報を更新する */
  def update(contentType: ContentType): Future[Int] = {
    db.run(contentTypes.filter(_.contentTypeId === contentType.contentTypeId).update(contentType))
  }

  /** データを削除する */
  def delete(contentTypeId: String): Future[Int] = {
    db.run(contentTypes.filter(_.contentTypeId === contentTypeId).delete)
  }
}
