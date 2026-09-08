package services
import models.db.{
  CommentNotificationModel,
  FollowNotificationModel,
  NotificationModel,
  ReactionNotificationModel,
  SystemNotificationModel
}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import models._
import repositories._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import shared.api.*
import models.db.TableQueries
import play.api.Logger

@Singleton
class NotificationService @Inject() (
    notificationRepo: NotificationModelRepository,
    userRepo: UserRepository,
    notificationDispatcher: NotificationDispatcher,
    protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  private val logger = Logger(this.getClass)

  private case class ContentTarget(ownerUserId: Option[String], link: String)

  private def dispatchPush(
      notificationId: Long,
      userId: String,
      notificationType: String,
      message: String,
      link: String
  ): Unit = {
    notificationDispatcher
      .dispatch(notificationId, userId, notificationType, message, link)
      .recover { case error => logger.warn(s"Notification $notificationId dispatch failed", error) }
    ()
  }

  def getNotificationsForUser(userId: String): Future[NotificationsResponse] = {
    notificationRepo.getNotificationsForUser(userId).flatMap { results =>
      // ユーザー情報を取得
      val userIds = results.flatMap {
        case (_, _, Some(rn), _, _) => Some(rn.actionUserId)
        case _                      => None
      }.distinct

      userRepo
        .findByIds(userIds)
        .flatMap { users =>
          val userMap = users.map(u => u.userId -> u.userName).toMap

          Future.traverse(results) { case (notification, systemOpt, reactionOpt, commentOpt, followOpt) =>
            val targetLink = reactionOpt
              .map(rn => resolveContentTarget(rn.contentTypeId, rn.contentId).map(_.map(_.link).getOrElse("/")))
              .orElse(
                followOpt
                  .map(fn => resolveContentTarget(fn.contentTypeId, fn.contentId).map(_.map(_.link).getOrElse("/")))
              )
              .getOrElse(Future.successful("/"))

            targetLink.map { link =>
              buildNotificationView(notification, systemOpt, reactionOpt, commentOpt, followOpt, userMap, link)
            }
          }
        }
        .map(NotificationsResponse.apply)
    }
  }

  private def buildNotificationView(
      notification: NotificationModel,
      systemOpt: Option[SystemNotificationModel],
      reactionOpt: Option[ReactionNotificationModel],
      commentOpt: Option[CommentNotificationModel],
      followOpt: Option[FollowNotificationModel],
      userMap: Map[String, String],
      targetLink: String
  ): NotificationViewValue = {
    val (notificationType, message, link, actorName) = (systemOpt, reactionOpt, commentOpt, followOpt) match {
      case (_, Some(rn), _, _) =>
        val actor = userMap.getOrElse(rn.actionUserId, "誰か")
        val contentType = rn.contentTypeId match {
          case "1" => "語釈"
          case "2" => "用例"
          case "3" => "スレッド"
          case "4" => "コメント"
          case _   => "投稿"
        }
        ("REACTION", s"$actor さんがあなたの${contentType}にリアクションしました。", targetLink, Some(actor))

      case (_, _, Some(cn), _) if cn.notificationKind == "BEST_ANSWER" =>
        ("COMMENT", "あなたの回答がベストアンサーに選ばれました。", s"/threads/${cn.threadId}", None)

      case (_, _, Some(cn), _) =>
        ("COMMENT", "あなたのスレッドまたはコメントに返信がつきました。", s"/threads/${cn.threadId}", None)

      case (_, _, _, Some(fn)) =>
        val followType = fn.followType match {
          case "TAG"  => "タグ"
          case "WORD" => "単語"
          case _      => "投稿"
        }
        ("FOLLOW", s"フォロー中の${followType}に新しい投稿がありました。", targetLink, None)

      case (system, _, _, _) =>
        ("SYSTEM", system.map(_.notificationsText).getOrElse("システム通知"), "/", None)
    }

    NotificationViewValue(
      notificationId = notification.notificationsId.getOrElse(0L),
      notificationType = notificationType,
      message = message,
      date = notification.notificationsDate,
      isRead = notification.isRead,
      link = link,
      actorName = actorName,
      actorIcon = None
    )
  }

  private def resolveContentTarget(contentTypeId: String, contentId: Long): Future[Option[ContentTarget]] = {
    val action = contentTypeId match {
      case "1" =>
        TableQueries.meanings
          .filter(m => m.meaningId === contentId && !m.isDeleted)
          .map(m => (m.userId, m.wordId, m.tagId))
          .result
          .headOption
          .map(_.map { case (owner, wordId, tagId) => ContentTarget(owner, s"/word/$wordId/tag/$tagId/meaning") })
      case "2" =>
        TableQueries.examples
          .filter(e => e.exampleId === contentId && !e.isDeleted)
          .map(e => (e.userId, e.wordId, e.tagId))
          .result
          .headOption
          .map(_.map { case (owner, wordId, tagId) => ContentTarget(owner, s"/word/$wordId/tag/$tagId/example") })
      case "3" =>
        TableQueries.threads
          .filter(t => t.threadId === contentId && !t.isDeleted)
          .map(_.userId)
          .result
          .headOption
          .map(_.map(owner => ContentTarget(owner, s"/threads/$contentId")))
      case "4" =>
        TableQueries.comments
          .filter(c => c.commentId === contentId && !c.isDeleted)
          .map(c => (c.userId, c.threadId))
          .result
          .headOption
          .map(_.map { case (owner, threadId) => ContentTarget(owner, s"/threads/$threadId") })
      case _ => DBIO.successful(None)
    }

    db.run(action)
  }

  def hasUnreadNotifications(userId: String): Future[Boolean] = {
    notificationRepo.hasUnread(userId)
  }

  def getUnreadCount(userId: String): Future[Int] = {
    notificationRepo.getUnreadCount(userId)
  }

  def markAsRead(notificationId: Long, userId: String): Future[Int] = {
    notificationRepo.markAsRead(notificationId, userId)
  }

  def markAllAsRead(userId: String): Future[Int] = {
    notificationRepo.markAllAsRead(userId)
  }

  /** コメント通知を生成（スレッド作成者・親コメント作成者への通知） */
  def createCommentNotification(
      threadId: Long,
      commentId: Long,
      parentCommentId: Option[Long],
      commentUserId: String
  ): Future[Unit] = {
    // 1. スレッド作成者を取得
    val threadOwnerQuery = TableQueries.threads.filter(_.threadId === threadId).map(_.userId).result.headOption

    // 2. 親コメントが存在する場合は親コメント作成者を取得
    val parentOwnerQuery = parentCommentId match {
      case Some(parentId) =>
        TableQueries.comments.filter(_.commentId === parentId).map(_.userId).result.headOption
      case None =>
        DBIO.successful(None)
    }

    db.run(threadOwnerQuery.zip(parentOwnerQuery)).flatMap { case (threadOwnerOpt, parentOwnerOpt) =>
      // 通知対象ユーザーを収集（重複排除 & 自分自身は除く）
      val notifyUserIds = (threadOwnerOpt.flatten.toSeq ++ parentOwnerOpt.flatten.toSeq).distinct
        .filterNot(_ == commentUserId)

      if (notifyUserIds.isEmpty) {
        Future.successful(())
      } else {
        val actions = notifyUserIds.map { userId =>
          for {
            // 通知レコードを作成
            notificationId <- (TableQueries.notifications returning TableQueries.notifications.map(
              _.notificationsId
            )) += NotificationModel(
              notificationsId = None,
              userId = userId,
              notificationsDate = System.currentTimeMillis(),
              isRead = false,
              notificationTypesId = "4"
            )
            // コメント通知レコードを作成
            _ <- TableQueries.commentNotifications += CommentNotificationModel(
              notificationsId = notificationId,
              threadId = threadId,
              replyCommentId = commentId,
              replyToCommentId = parentCommentId,
              notificationKind = "COMMENT"
            )
          } yield (notificationId, userId)
        }

        db.run(DBIO.sequence(actions).transactionally).map { notifications =>
          notifications.foreach { case (notificationId, targetUserId) =>
            dispatchPush(
              notificationId,
              targetUserId,
              "COMMENT",
              "あなたのスレッドまたはコメントに返信がつきました。",
              s"/threads/$threadId"
            )
          }
        }
      }
    }
  }

  /** リアクション追加時に対象コンテンツの投稿者へ通知する。 */
  def createReactionNotification(
      contentTypeId: String,
      contentId: Long,
      reactionId: Long,
      actionUserId: String
  ): Future[Unit] = {
    resolveContentTarget(contentTypeId, contentId).flatMap {
      case Some(ContentTarget(Some(ownerUserId), link)) if ownerUserId != actionUserId =>
        val action = for {
          notificationId <- (TableQueries.notifications returning TableQueries.notifications.map(
            _.notificationsId
          )) += NotificationModel(
            notificationsId = None,
            userId = ownerUserId,
            notificationsDate = System.currentTimeMillis(),
            isRead = false,
            notificationTypesId = "2"
          )
          _ <- TableQueries.reactionNotifications += ReactionNotificationModel(
            notificationsId = notificationId,
            actionUserId = actionUserId,
            contentTypeId = contentTypeId,
            contentId = contentId,
            reactionId = reactionId
          )
        } yield notificationId

        db.run(action.transactionally).map { notificationId =>
          val contentName = contentTypeId match {
            case "1" => "語釈"
            case "2" => "用例"
            case "3" => "スレッド"
            case "4" => "コメント"
            case _   => "投稿"
          }
          dispatchPush(
            notificationId,
            ownerUserId,
            "REACTION",
            s"あなたの${contentName}にリアクションがつきました。",
            link
          )
        }
      case _ => Future.successful(())
    }
  }

  /** ベストアンサーに選ばれたコメントの投稿者へ通知する。 */
  def createBestAnswerNotification(
      threadId: Long,
      commentId: Long,
      answerUserId: String,
      selectedByUserId: String
  ): Future[Unit] = {
    if (answerUserId == selectedByUserId) {
      Future.successful(())
    } else {
      val action = for {
        notificationId <- (TableQueries.notifications returning TableQueries.notifications.map(
          _.notificationsId
        )) += NotificationModel(
          notificationsId = None,
          userId = answerUserId,
          notificationsDate = System.currentTimeMillis(),
          isRead = false,
          notificationTypesId = "4"
        )
        _ <- TableQueries.commentNotifications += CommentNotificationModel(
          notificationsId = notificationId,
          threadId = threadId,
          replyCommentId = commentId,
          replyToCommentId = None,
          notificationKind = "BEST_ANSWER"
        )
      } yield notificationId

      db.run(action.transactionally).map { notificationId =>
        dispatchPush(
          notificationId,
          answerUserId,
          "COMMENT",
          "あなたの回答がベストアンサーに選ばれました。",
          s"/threads/$threadId"
        )
      }
    }
  }

  /** フォロー通知を生成（タグ・単語への新規投稿時） */
  def createFollowNotification(
      wordId: Long,
      tagId: Long,
      contentTypeId: String,
      contentId: Long,
      followType: String,
      postUserId: String
  ): Future[Unit] = {
    // このタグ・単語をフォローしているユーザーを取得
    val followersQuery = followType match {
      case "TAG" =>
        // タグのみをフォローしているユーザー
        TableQueries.follows.filter(f => f.tagId === tagId && f.wordId.isEmpty)
      case "WORD" =>
        // 単語をフォローしているユーザー
        TableQueries.follows.filter(f => f.wordId === Some(wordId) && f.tagId === tagId)
      case _ =>
        TableQueries.follows.filter(_ => false)
    }

    db.run(followersQuery.map(_.userId).result).flatMap { followerIds =>
      // 自分自身は通知対象から除外
      val targetUserIds = followerIds.filterNot(_ == postUserId)

      if (targetUserIds.isEmpty) {
        Future.successful(())
      } else {
        // 通知を順番に作成（トランザクション内）
        val actions = targetUserIds.map { followerId =>
          for {
            // 通知レコードを作成
            notificationId <- (TableQueries.notifications returning TableQueries.notifications.map(
              _.notificationsId
            )) += NotificationModel(
              notificationsId = None,
              userId = followerId,
              notificationsDate = System.currentTimeMillis(),
              isRead = false,
              notificationTypesId = "3" // フォロー通知
            )
            // フォロー通知レコードを作成
            _ <- TableQueries.followNotifications += FollowNotificationModel(
              notificationsId = notificationId,
              contentTypeId = contentTypeId,
              contentId = contentId,
              followType = followType
            )
          } yield (notificationId, followerId)
        }

        db.run(DBIO.sequence(actions).transactionally).map { notifications =>
          val followedContent = if (followType == "TAG") "タグ" else "単語"
          val link = contentTypeId match {
            case "1" => s"/word/$wordId/tag/$tagId/meaning"
            case "2" => s"/word/$wordId/tag/$tagId/example"
            case "3" => s"/threads/$contentId"
            case _   => "/"
          }
          notifications.foreach { case (notificationId, followerId) =>
            dispatchPush(
              notificationId,
              followerId,
              "FOLLOW",
              s"フォロー中の${followedContent}に新しい投稿がありました。",
              link
            )
          }
        }
      }
    }
  }

}
