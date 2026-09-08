package shared.api

import upickle.default.ReadWriter

case class NotificationViewValue(
  notificationId: Long,
  notificationType: String,
  message: String,
  date: Long,
  isRead: Boolean,
  link: String,
  actorName: Option[String],
  actorIcon: Option[String]
) derives ReadWriter

case class NotificationsResponse(notifications: Seq[NotificationViewValue]) derives ReadWriter
