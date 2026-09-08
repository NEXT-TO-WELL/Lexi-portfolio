package repositories
import models.db.ReactionNotificationModel

import models.db.TableQueries.reactionNotifications
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ReactionNotificationModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ReactionNotificationModel]] = {
    db.run(reactionNotifications.result)
  }

  /** IDでデータを1つ取得する */
  def findById(notificationsId: Long): Future[Option[ReactionNotificationModel]] = {
    db.run(reactionNotifications.filter(_.notificationsId === notificationsId).result.headOption)
  }

  /** データを新規登録する */
  def create(notification: ReactionNotificationModel): Future[Int] = {
    db.run(reactionNotifications += notification)
  }

  /** データ情報を更新する */
  def update(reactionNotification: ReactionNotificationModel): Future[Int] = {
    db.run(
      reactionNotifications
        .filter(_.notificationsId === reactionNotification.notificationsId)
        .update(reactionNotification)
    )
  }

  /** データを削除する */
  def delete(notificationsId: Long): Future[Int] = {
    db.run(reactionNotifications.filter(_.notificationsId === notificationsId).delete)
  }
}
