package repositories
import models.db.NotificationType

import models.db.TableQueries.notificationTypes
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class NotificationTypeRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[NotificationType]] = {
    db.run(notificationTypes.result)
  }

  /** IDでデータを1つ取得する */
  def findById(notificationTypeId: String): Future[Option[NotificationType]] = {
    db.run(notificationTypes.filter(_.notificationTypesId === notificationTypeId).result.headOption)
  }

  /** データを新規登録する */
  def create(notificationType: NotificationType): Future[Int] = {
    db.run(notificationTypes += notificationType)
  }

  /** データ情報を更新する */
  def update(notificationType: NotificationType): Future[Int] = {
    db.run(
      notificationTypes.filter(_.notificationTypesId === notificationType.notificationTypesId).update(notificationType)
    )
  }

  /** データを削除する */
  def delete(notificationTypeId: String): Future[Int] = {
    db.run(notificationTypes.filter(_.notificationTypesId === notificationTypeId).delete)
  }
}
