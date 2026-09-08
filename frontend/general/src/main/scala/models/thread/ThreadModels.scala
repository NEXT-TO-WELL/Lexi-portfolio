package models

import upickle.default.ReadWriter
import shared.api.ContentQuoteViewValue

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
case class QuestionStatusViewValue(isResolved: Boolean, resolvedDate: Option[String], bestCommentId: Option[Long])
    derives ReadWriter
case class ThreadViewValue(
    threadId: Long,
    userId: Option[String],
    displayId: Option[String],
    userName: String,
    threadTitle: String,
    threadCreateDate: Long,
    threadType: ThreadType,
    wordId: Long,
    tagId: Long,
    questionStatus: Option[QuestionStatusViewValue] = None,
    commentCount: Int,
    quote: Option[ContentQuoteViewValue] = None
) derives ReadWriter
case class CommentModel(
    commentId: Option[Long],
    threadId: Long,
    userId: Option[String],
    parentCommentId: Option[Long],
    commentDate: Long,
    commentText: String,
    commentGoodCount: Int,
    commentBadCount: Int,
    isDeleted: Boolean,
    lastModified: Long
) derives ReadWriter
case class CommentViewValue(
    comment: CommentModel,
    userName: String,
    displayId: Option[String],
    isBestAnswer: Boolean,
    isGood: Boolean = false,
    isBad: Boolean = false,
    quote: Option[ContentQuoteViewValue] = None
) derives ReadWriter
case class ThreadDetailResponse(thread: ThreadViewValue, comments: Seq[CommentViewValue]) derives ReadWriter
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
