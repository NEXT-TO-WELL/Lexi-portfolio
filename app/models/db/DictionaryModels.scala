package models.db

import upickle.default.ReadWriter

case class Word(wordId: Option[Long], wordMain: String) derives ReadWriter

case class TagModel(tagId: Option[Long], tagName: String) derives ReadWriter

case class ContentType(contentTypeId: String, contentTypeName: String) derives ReadWriter

case class ReportType(reportTypeId: String, reportTypeName: String) derives ReadWriter

case class ReportStatus(reportStatusId: String, reportStatusName: String) derives ReadWriter

case class NotificationType(notificationTypesId: String, notificationTypesName: String) derives ReadWriter

case class WordTagModel(
    wordId: Long,
    tagId: Long,
    userId: Option[String],
    wordCreateDate: Long,
    wordReading: String
) derives ReadWriter
