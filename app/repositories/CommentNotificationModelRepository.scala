package repositories
import models.db.CommentNotificationModel

import models.db.TableQueries.commentNotifications
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class CommentNotificationModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[CommentNotificationModel]] = {
    db.run(commentNotifications.result)
  }

  /** IDでデータを1つ取得する */
  def findById(notificationsId: Long): Future[Option[CommentNotificationModel]] = {
    db.run(commentNotifications.filter(_.notificationsId === notificationsId).result.headOption)
  }

  /** データを新規登録する */
  def create(notification: CommentNotificationModel): Future[Int] = {
    db.run(commentNotifications += notification)
  }

  /** データ情報を更新する */
  def update(commentNotification: CommentNotificationModel): Future[Int] = {
    db.run(
      commentNotifications.filter(_.notificationsId === commentNotification.notificationsId).update(commentNotification)
    )
  }

  /** データを削除する */
  def delete(notificationsId: Long): Future[Int] = {
    db.run(commentNotifications.filter(_.notificationsId === notificationsId).delete)
  }
}
