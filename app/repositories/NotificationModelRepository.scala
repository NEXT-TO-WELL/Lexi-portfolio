package repositories
import models.db.{
  CommentNotificationModel,
  FollowNotificationModel,
  NotificationModel,
  ReactionNotificationModel,
  SystemNotificationModel
}
import models.db.TableQueries

import models.*
import models.db.TableQueries.notifications
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class NotificationModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[NotificationModel]] = {
    db.run(notifications.result)
  }

  /** IDでデータを1つ取得する */
  def findById(notificationsId: Option[Long]): Future[Option[NotificationModel]] = {
    db.run(notifications.filter(_.notificationsId === notificationsId).result.headOption)
  }

  def hasUnread(userId: String): Future[Boolean] = {
    val query = notifications.filter(n => n.userId === userId && !n.isRead).exists
    db.run(query.result)
  }

  def getUnreadCount(userId: String): Future[Int] = {
    val query = notifications.filter(n => n.userId === userId && !n.isRead).length
    db.run(query.result)
  }

  /** ユーザーの通知を取得（最新50件） */
  def getNotificationsForUser(userId: String): Future[Seq[
    (
        NotificationModel,
        Option[SystemNotificationModel],
        Option[ReactionNotificationModel],
        Option[CommentNotificationModel],
        Option[FollowNotificationModel]
    )
  ]] = {
    val query = notifications
      .filter(_.userId === userId)
      .joinLeft(TableQueries.systemNotifications)
      .on(_.notificationsId === _.notificationsId)
      .joinLeft(TableQueries.reactionNotifications)
      .on(_._1.notificationsId === _.notificationsId)
      .joinLeft(TableQueries.commentNotifications)
      .on(_._1._1.notificationsId === _.notificationsId)
      .joinLeft(TableQueries.followNotifications)
      .on(_._1._1._1.notificationsId === _.notificationsId)
      .sortBy(_._1._1._1._1.notificationsDate.desc)
      .take(50)
      .map { case ((((n, sn), rn), cn), fn) => (n, sn, rn, cn, fn) }

    db.run(query.result)
  }

  /** 通知を既読にする */
  def markAsRead(notificationId: Long, userId: String): Future[Int] = {
    val query = notifications
      .filter(n => n.notificationsId === Some(notificationId) && n.userId === userId)
      .map(_.isRead)
      .update(true)
    db.run(query)
  }

  /** 全ての通知を既読にする */
  def markAllAsRead(userId: String): Future[Int] = {
    val query = notifications
      .filter(n => n.userId === userId && !n.isRead)
      .map(_.isRead)
      .update(true)
    db.run(query)
  }

  /** データを新規登録する */
  def create(notification: NotificationModel): Future[Int] = {
    db.run(notifications += notification)
  }

  /** データ情報を更新する */
  def update(notification: NotificationModel): Future[Int] = {
    db.run(notifications.filter(_.notificationsId === notification.notificationsId).update(notification))
  }

  /** データを削除する */
  def delete(notificationsId: Option[Long]): Future[Int] = {
    db.run(notifications.filter(_.notificationsId === notificationsId).delete)
  }
}
