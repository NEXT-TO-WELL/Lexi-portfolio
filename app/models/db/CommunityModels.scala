package models.db

import upickle.default.ReadWriter

case class ReactionModel(id: Option[Long], userId: String, contentTypeId: String, contentId: Long, reactionDate: Long)
    derives ReadWriter

case class ThreadModel(
    threadId: Option[Long],
    wordId: Long,
    tagId: Long,
    userId: Option[String],
    threadTypeId: String,
    threadCreateDate: Long,
    threadTitle: String,
    threadCommentCount: Int,
    isDeleted: Boolean,
    lastModified: Long
) derives ReadWriter

case class ThreadQuestionModel(
    threadId: Long,
    isDeleted: Boolean,
    resolvedDate: Option[Long],
    bestCommentId: Option[Long]
) derives ReadWriter

case class CommentReactionModel(
    commentReactionId: Option[Long],
    userId: Option[String],
    commentId: Long,
    isGood: Boolean,
    commentReactionDate: Long,
    lastModified: Long
) derives ReadWriter

case class ContentQuoteModel(
    quoteId: Option[Long],
    sourceContentTypeId: String,
    sourceContentId: Long,
    targetContentTypeId: String,
    targetContentId: Long,
    createdByUserId: String,
    createdAt: Long,
    lastModified: Long,
    isDeleted: Boolean,
    deletedByUserId: Option[String],
    deletedAt: Option[Long]
) derives ReadWriter
