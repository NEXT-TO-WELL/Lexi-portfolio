package models

import upickle.default.ReadWriter

case class ThreadType(threadTypeId: String, threadTypeName: String) derives ReadWriter
case class ContentType(contentTypeId: String, contentTypeName: String) derives ReadWriter
case class ReportType(reportTypeId: String, reportTypeName: String) derives ReadWriter
case class ReportStatus(reportStatusId: String, reportStatusName: String) derives ReadWriter
case class NotificationType(notificationTypesId: String, notificationTypesName: String) derives ReadWriter
case class ReactionModel(id: Option[Long], userId: String, contentTypeId: String, contentId: Long, reactionDate: Long)
    derives ReadWriter
