package shared.api

import upickle.default.ReadWriter

object ContentQuoteTypes {
  val Meaning = "1"
  val Example = "2"
  val Thread = "3"
  val Comment = "4"

  def isSource(contentTypeId: String): Boolean =
    contentTypeId == Meaning || contentTypeId == Example
}

case class ContentQuoteReference(
    contentTypeId: String,
    contentId: Long
) derives ReadWriter

case class ContentQuoteViewValue(
    sourceContentTypeId: String,
    sourceContentId: Long,
    isSourceDeleted: Boolean,
    contentText: Option[String],
    userName: Option[String],
    displayId: Option[String],
    wordId: Option[Long],
    tagId: Option[Long],
    wordMain: Option[String],
    tagName: Option[String]
) derives ReadWriter
