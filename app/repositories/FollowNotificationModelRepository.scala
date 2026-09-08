package repositories
import models.db.FollowNotificationModel

import models.db.TableQueries.followNotifications
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class FollowNotificationModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[FollowNotificationModel]] = {
    db.run(followNotifications.result)
  }

  /** IDでデータを1つ取得する */
  def findById(notificationsId: Long): Future[Option[FollowNotificationModel]] = {
    db.run(followNotifications.filter(_.notificationsId === notificationsId).result.headOption)
  }

  /** データを新規登録する */
  def create(notification: FollowNotificationModel): Future[Int] = {
    db.run(followNotifications += notification)
  }

  /** データ情報を更新する */
  def update(followNotification: FollowNotificationModel): Future[Int] = {
    db.run(
      followNotifications.filter(_.notificationsId === followNotification.notificationsId).update(followNotification)
    )
  }

  /** データを削除する */
  def delete(notificationsId: Long): Future[Int] = {
    db.run(followNotifications.filter(_.notificationsId === notificationsId).delete)
  }
}
