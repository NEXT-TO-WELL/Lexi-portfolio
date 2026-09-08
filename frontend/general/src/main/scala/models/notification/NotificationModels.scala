package models

import upickle.default.ReadWriter

case class NotificationModel(
    notificationsId: Option[Long],
    userId: String,
    notificationsDate: Long,
    isRead: Boolean,
    notificationTypesId: String
) derives ReadWriter
case class SystemNotificationModel(notificationsId: Long, notificationsText: String) derives ReadWriter
case class ReactionNotificationModel(
    notificationsId: Long,
    actionUserId: String,
    contentTypeId: String,
    contentId: Long,
    reactionId: Long
) derives ReadWriter
case class FollowNotificationModel(notificationsId: Long, contentTypeId: String, contentId: Long, followType: String)
    derives ReadWriter
case class CommentNotificationModel(
    notificationsId: Long,
    threadId: Long,
    replyCommentId: Long,
    replyToCommentId: Option[Long]
) derives ReadWriter
