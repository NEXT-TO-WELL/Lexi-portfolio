package repositories
import models.db.SystemNotificationModel

import models.db.TableQueries.systemNotifications
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class SystemNotificationModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[SystemNotificationModel]] = {
    db.run(systemNotifications.result)
  }

  /** IDでデータを1つ取得する */
  def findById(notificationsId: Long): Future[Option[SystemNotificationModel]] = {
    db.run(systemNotifications.filter(_.notificationsId === notificationsId).result.headOption)
  }

  /** データを新規登録する */
  def create(notification: SystemNotificationModel): Future[Int] = {
    db.run(systemNotifications += notification)
  }

  /** データ情報を更新する */
  def update(systemNotification: SystemNotificationModel): Future[Int] = {
    db.run(
      systemNotifications.filter(_.notificationsId === systemNotification.notificationsId).update(systemNotification)
    )
  }

  /** データを削除する */
  def delete(notificationsId: Long): Future[Int] = {
    db.run(systemNotifications.filter(_.notificationsId === notificationsId).delete)
  }
}
