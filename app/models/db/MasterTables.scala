package models.db

import slick.jdbc.MySQLProfile.api._
import models.*
import java.sql.Timestamp

// Slickに「Long型のカラムをDBのTimestampとして扱ってね」と教える設定
val longToTimestampColumnType: BaseColumnType[Long] =
  MappedColumnType.base[Long, Timestamp](
    millis => new Timestamp(millis), // Model -> DB
    ts => ts.getTime // DB -> Model
  )

class RolesTable(tag: Tag) extends Table[Role](tag, "Roles") {
  def roleId = column[String]("role_id", O.PrimaryKey, O.Length(20))

  def roleName = column[String]("role_name", O.Length(50))

  def description = column[Option[String]]("description", O.Length(255))

  def * = (roleId, roleName, description).mapTo[Role]
}

class UsersTable(tag: Tag) extends Table[User](tag, "Users") {
  def userId = column[String]("user_id", O.PrimaryKey, O.Length(50))
  def displayId = column[String]("display_id", O.Length(32)) // ← 追加: 公開用ランダムID
  def userName = column[String]("user_name", O.Length(30))
  def userPassHash = column[String]("user_pass_hash", O.Length(255))
  // role_idを追加。デフォルト値を"user"に設定
  def roleId = column[String]("role_id", O.Default("user"), O.Length(20))
  def userRegistrationDate = column[Option[Long]]("user_registration_date")(longToTimestampColumnType.optionType)
  def tokenVersion = column[Int]("token_version", O.Default(0))

  // 外部キー制約
  def role = foreignKey("role_fk", roleId, TableQuery[RolesTable])(_.roleId)

  def * = (userId, displayId, userName, userPassHash, roleId, userRegistrationDate, tokenVersion).mapTo[User]
}

class WordsTable(tag: Tag) extends Table[Word](tag, "Words") {
  def wordId = column[Long]("word_id", O.PrimaryKey, O.AutoInc)
  def wordMain = column[String]("word_main", O.Length(100))
  def * = (wordId.?, wordMain).mapTo[Word]
}

class TagsTable(tag: Tag) extends Table[TagModel](tag, "Tags") {
  def tagId = column[Long]("tag_id", O.PrimaryKey, O.AutoInc)
  def tagName = column[String]("tag_name", O.Length(50))
  def * = (tagId.?, tagName).mapTo[TagModel]
}

class ThreadTypesTable(tag: Tag) extends Table[ThreadType](tag, "Thread_Types") {
  def threadTypeId = column[String]("thread_type_id", O.PrimaryKey, O.Length(6))
  def threadTypeName = column[String]("thread_type_name", O.Length(50))
  def * = (threadTypeId, threadTypeName).mapTo[ThreadType]
}

class ContentTypesTable(tag: Tag) extends Table[ContentType](tag, "Content_Types") {
  def contentTypeId = column[String]("content_type_id", O.PrimaryKey, O.Length(6))
  def contentTypeName = column[String]("content_type_name", O.Length(50))
  def * = (contentTypeId, contentTypeName).mapTo[ContentType]
}

class ReportTypesTable(tag: Tag) extends Table[ReportType](tag, "Report_Types") {
  def reportTypeId = column[String]("report_type_id", O.PrimaryKey, O.Length(6))
  def reportTypeName = column[String]("report_type_name", O.Length(50))
  def * = (reportTypeId, reportTypeName).mapTo[ReportType]
}

class ReportStatusTable(tag: Tag) extends Table[ReportStatus](tag, "Report_Status") {
  def reportStatusId = column[String]("report_status_id", O.PrimaryKey, O.Length(6))
  def reportStatusName = column[String]("report_status_name", O.Length(50))
  def * = (reportStatusId, reportStatusName).mapTo[ReportStatus]
}

class NotificationTypesTable(tag: Tag) extends Table[NotificationType](tag, "Notification_Types") {
  def notificationTypesId = column[String]("notification_types_id", O.PrimaryKey, O.Length(6))
  def notificationTypesName = column[String]("notification_types_name", O.Length(50))
  def * = (notificationTypesId, notificationTypesName).mapTo[NotificationType]
}

class WordTagsTable(tag: Tag) extends Table[WordTagModel](tag, "Word_Tags") {
  def wordId = column[Long]("word_id")
  def tagId = column[Long]("tag_id")
  def userId = column[Option[String]]("user_id", O.Length(50))
  def wordCreateDate =
    column[Long]("word_create_date", O.Default(System.currentTimeMillis()))(longToTimestampColumnType)
  def wordReading = column[String]("word_reading", O.Length(100))

  // 複合主キー
  def pk = primaryKey("pk_word_tags", (wordId, tagId))

  // 外部キー
  def word = foreignKey("FK_Word_Tags_Words", wordId, TableQueries.words)(_.wordId)
  def tagRef = foreignKey("FK_Word_Tags_Tags", tagId, TableQueries.tags)(_.tagId)
  def user = foreignKey("FK_Word_Tags_Users", userId, TableQueries.users)(_.userId.?)

  def * = (wordId, tagId, userId, wordCreateDate, wordReading).mapTo[WordTagModel]
}

// --- Meanings ---
class MeaningsTable(tag: Tag) extends Table[MeaningModel](tag, "Meanings") {
  def meaningId = column[Long]("meaning_id", O.PrimaryKey, O.AutoInc)
  def wordId = column[Long]("word_id")
  def tagId = column[Long]("tag_id")
  def userId = column[Option[String]]("user_id")
  def meaningPostedDate = column[Long]("meaning_posted_date")(longToTimestampColumnType)
  def meaningText = column[String]("meaning_text", O.Length(1000))
  def isJoke = column[Boolean]("is_joke", O.Default(false))
  def meaningGoodCount = column[Int]("meaning_good_count", O.Default(0))
  def meaningHelpfulCount = column[Int]("meaning_helpful_count", O.Default(0))
  def meaningFunnyCount = column[Int]("meaning_funny_count", O.Default(0))
  def isDeleted = column[Boolean]("is_deleted", O.Default(false))
  def lastModified = column[Long]("last_modified")(longToTimestampColumnType)

  // Word_Tags への複合外部キー
  def wordTag = foreignKey("FK_Meanings_Word_Tags", (wordId, tagId), TableQueries.wordTags)(x => (x.wordId, x.tagId))

  def * = (
    meaningId.?,
    wordId,
    tagId,
    userId,
    meaningPostedDate,
    meaningText,
    isJoke,
    meaningGoodCount,
    meaningHelpfulCount,
    meaningFunnyCount,
    isDeleted,
    lastModified
  ).mapTo[MeaningModel]
}

// --- Examples --- (Meanings とほぼ同様の構造)
class ExamplesTable(tag: Tag) extends Table[ExampleModel](tag, "Examples") {
  def exampleId = column[Long]("example_id", O.PrimaryKey, O.AutoInc)
  def wordId = column[Long]("word_id")
  def tagId = column[Long]("tag_id")
  def userId = column[Option[String]]("user_id")
  def examplePostedDate = column[Long]("example_posted_date")(longToTimestampColumnType)
  def exampleText = column[String]("example_text", O.Length(1000))
  def isJoke = column[Boolean]("is_joke", O.Default(false))
  def exampleGoodCount = column[Int]("example_good_count", O.Default(0))
  def exampleHelpfulCount = column[Int]("example_helpful_count", O.Default(0))
  def exampleFunnyCount = column[Int]("example_funny_count", O.Default(0))
  def isDeleted = column[Boolean]("is_deleted", O.Default(false))
  def lastModified = column[Long]("last_modified")(longToTimestampColumnType)

  def wordTag = foreignKey("FK_Examples_Word_Tags", (wordId, tagId), TableQueries.wordTags)(x => (x.wordId, x.tagId))

  def * = (
    exampleId.?,
    wordId,
    tagId,
    userId,
    examplePostedDate,
    exampleText,
    isJoke,
    exampleGoodCount,
    exampleHelpfulCount,
    exampleFunnyCount,
    isDeleted,
    lastModified
  ).mapTo[ExampleModel]
}

// --- Trends (Hot, New, Buzz) ---
abstract class TrendTable[T](tag: Tag, tableName: String) extends Table[T](tag, tableName) {
  def wordId = column[Long]("word_id")
  def tagId = column[Long]("tag_id")
  def wordTag =
    foreignKey(s"FK_${tableName}_Word_Tags", (wordId, tagId), TableQueries.wordTags)(x => (x.wordId, x.tagId))
}

class TrendHotTable(tag: Tag) extends TrendTable[TrendHotModel](tag, "Trend_Hot") {
  def hotRank = column[Int]("hot_rank", O.PrimaryKey)
  def commentCount = column[Int]("comment_count")

  def * = (hotRank, wordId, tagId, commentCount).mapTo[TrendHotModel]
}

class TrendNewTable(tag: Tag) extends TrendTable[TrendNewModel](tag, "Trend_New") {
  def newRank = column[Int]("new_rank", O.PrimaryKey)
  def goodCount = column[Int]("good_count")
  def * = (newRank, wordId, tagId, goodCount).mapTo[TrendNewModel]
}

class TrendBuzzTable(tag: Tag) extends TrendTable[TrendBuzzModel](tag, "Trend_Buzz") {
  def buzzRank = column[Int]("buzz_rank", O.PrimaryKey)
  def goodCount = column[Int]("good_count")
  def * = (buzzRank, wordId, tagId, goodCount).mapTo[TrendBuzzModel]
}

abstract class ReactionsTable(tag: Tag, tableName: String) extends Table[ReactionModel](tag, tableName) {
  def id = column[Long](tableName.toLowerCase.split("_")(1) + "_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def contentTypeId = column[String]("content_type_id", O.Length(6))
  def contentId = column[Long]("content_id")
  def reactionDate = column[Long]("reaction_date", O.Default(System.currentTimeMillis()))(longToTimestampColumnType)

  // ユニーク制約 (UQ_..._user_content)
  def idx = index(s"UQ_${tableName}_user_content", (userId, contentTypeId, contentId), unique = true)

  // 外部キー
  def user = foreignKey(s"FK_${tableName}_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def contentType = foreignKey(s"FK_${tableName}_Content_Types", contentTypeId, TableQueries.contentTypes)(
    _.contentTypeId,
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (id.?, userId, contentTypeId, contentId, reactionDate).mapTo[ReactionModel]
}

class ReactionGoodTable(tag: Tag) extends ReactionsTable(tag, "Reaction_Good")
class ReactionHelpfulTable(tag: Tag) extends ReactionsTable(tag, "Reaction_Helpful")
class ReactionFunnyTable(tag: Tag) extends ReactionsTable(tag, "Reaction_Funny")

// --- Threads ---
class ThreadsTable(tag: Tag) extends Table[ThreadModel](tag, "Threads") {
  def threadId = column[Long]("thread_id", O.PrimaryKey, O.AutoInc)
  def wordId = column[Long]("word_id")
  def tagId = column[Long]("tag_id")
  def userId = column[Option[String]]("user_id", O.Length(50))
  def threadTypeId = column[String]("thread_type_id", O.Length(6))
  def threadCreateDate =
    column[Long]("thread_create_date", O.Default(System.currentTimeMillis()))(longToTimestampColumnType)
  def threadTitle = column[String]("thread_title", O.Length(100))
  def threadCommentCount = column[Int]("thread_comment_count", O.Default(0))
  def isDeleted = column[Boolean]("is_deleted", O.Default(false))
  def lastModified = column[Long]("last_modified", O.Default(System.currentTimeMillis()))(longToTimestampColumnType)

  // 外部キー
  def wordTag = foreignKey("FK_Threads_Word_Tags", (wordId, tagId), TableQueries.wordTags)(
    x => (x.wordId, x.tagId),
    onUpdate = ForeignKeyAction.Cascade
  )
  def user = foreignKey("FK_Threads_Users", userId, TableQueries.users)(
    _.userId.?,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.SetNull
  )
  def threadType = foreignKey("FK_Threads_Tread_Types", threadTypeId, TableQueries.threadTypes)(
    _.threadTypeId,
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (
    threadId.?,
    wordId,
    tagId,
    userId,
    threadTypeId,
    threadCreateDate,
    threadTitle,
    threadCommentCount,
    isDeleted,
    lastModified
  ).mapTo[ThreadModel]
}

class CommentsTable(tag: Tag) extends Table[CommentModel](tag, "Comments") {
  def commentId = column[Long]("comment_id", O.PrimaryKey, O.AutoInc)
  def threadId = column[Long]("thread_id")
  def userId = column[Option[String]]("user_id", O.Length(50))
  def parentCommentId = column[Option[Long]]("parent_comment_id")
  def commentDate = column[Long]("comment_date", O.Default(System.currentTimeMillis()))(longToTimestampColumnType)
  def commentText = column[String]("comment_text", O.Length(400))
  def commentGoodCount = column[Int]("comment_good_count", O.Default(0))
  def commentBadCount = column[Int]("comment_bad_count", O.Default(0))
  def isDeleted = column[Boolean]("is_deleted", O.Default(false))
  def lastModified = column[Long]("last_modified")(longToTimestampColumnType)

  // 外部キー
  def thread = foreignKey("FK_Comments_Threads", threadId, TableQueries.threads)(_.threadId)
  def user = foreignKey("FK_Comments_Users", userId, TableQueries.users)(
    _.userId.?,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.SetNull
  )
  // 自己参照リレーション
  def parentComment = foreignKey("FK_Comments_Comments", parentCommentId, TableQueries.comments)(_.commentId.?)

  def * = (
    commentId.?,
    threadId,
    userId,
    parentCommentId,
    commentDate,
    commentText,
    commentGoodCount,
    commentBadCount,
    isDeleted,
    lastModified
  ).mapTo[CommentModel]
}

class ContentQuotesTable(tag: Tag) extends Table[ContentQuoteModel](tag, "Content_Quotes") {
  def quoteId = column[Long]("quote_id", O.PrimaryKey, O.AutoInc)
  def sourceContentTypeId = column[String]("source_content_type_id", O.Length(6))
  def sourceContentId = column[Long]("source_content_id")
  def targetContentTypeId = column[String]("target_content_type_id", O.Length(6))
  def targetContentId = column[Long]("target_content_id")
  def createdByUserId = column[String]("created_by_user_id", O.Length(50))
  def createdAt = column[Long]("created_at")(longToTimestampColumnType)
  def lastModified = column[Long]("last_modified")(longToTimestampColumnType)
  def isDeleted = column[Boolean]("is_deleted", O.Default(false))
  def deletedByUserId = column[Option[String]]("deleted_by_user_id", O.Length(50))
  def deletedAt = column[Long]("deleted_at")(longToTimestampColumnType).?

  def targetUnique = index("UQ_Content_Quotes_target", (targetContentTypeId, targetContentId), unique = true)
  def sourceIndex = index("IX_Content_Quotes_source", (sourceContentTypeId, sourceContentId, isDeleted))
  def sourceType = foreignKey("FK_Content_Quotes_source_type", sourceContentTypeId, TableQueries.contentTypes)(
    _.contentTypeId
  )
  def targetType = foreignKey("FK_Content_Quotes_target_type", targetContentTypeId, TableQueries.contentTypes)(
    _.contentTypeId
  )

  def * = (
    quoteId.?,
    sourceContentTypeId,
    sourceContentId,
    targetContentTypeId,
    targetContentId,
    createdByUserId,
    createdAt,
    lastModified,
    isDeleted,
    deletedByUserId,
    deletedAt
  ).mapTo[ContentQuoteModel]
}

// --- Thread_Questions ---
class ThreadQuestionsTable(tag: Tag) extends Table[ThreadQuestionModel](tag, "Thread_Questions") {
  def threadId = column[Long]("thread_id", O.PrimaryKey)
  def isDeleted = column[Boolean]("is_deleted", O.Default(false))
  def resolvedDate = column[Long]("resolved_date")(longToTimestampColumnType).?
  def bestCommentId = column[Option[Long]]("best_comment_id")

  def thread = foreignKey("FK_Questions_Threads", threadId, TableQueries.threads)(_.threadId)
  def bestComment = foreignKey("FK_Questions_Comments", bestCommentId, TableQueries.comments)(
    _.commentId.?,
    onDelete = ForeignKeyAction.SetNull
  )

  def * = (threadId, isDeleted, resolvedDate, bestCommentId).mapTo[ThreadQuestionModel]
}

// --- Comment_Reaction ---
class CommentReactionsTable(tag: Tag) extends Table[CommentReactionModel](tag, "Comment_Reaction") {
  def commentReactionId = column[Long]("comment_reaction_id", O.PrimaryKey, O.AutoInc)
  def userId = column[Option[String]]("user_id", O.Length(50))
  def commentId = column[Long]("comment_id")
  def isGood = column[Boolean]("is_good")
  def commentReactionDate = column[Long]("comment_reaction_date")(longToTimestampColumnType)
  def lastModified = column[Long]("last_modified")(longToTimestampColumnType)

  def idx = index("UQ_user_comment", (userId, commentId), unique = true)
  def user = foreignKey("FK_Comments_Reaction_Users", userId, TableQueries.users)(
    _.userId.?,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.SetNull
  )
  def comment = foreignKey("FK_Comments_Reaction_Comments", commentId, TableQueries.comments)(_.commentId)

  def * =
    (commentReactionId.?, userId, commentId, isGood, commentReactionDate, lastModified).mapTo[CommentReactionModel]
}

// --- Hidden_User ---
class HiddenUsersTable(tag: Tag) extends Table[HiddenUserModel](tag, "Hidden_User") {
  def userId = column[String]("user_id", O.Length(50))
  def targetUserId = column[String]("target_user_id", O.Length(50))
  def hiddenDate = column[Long]("hidden_date", O.Default(System.currentTimeMillis()))(longToTimestampColumnType)

  def pk = primaryKey("pk_hidden_user", (userId, targetUserId))
  def user = foreignKey("FK_Hidden_Self", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def targetUser = foreignKey("FK_Hidden_Target", targetUserId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )

  def * = (userId, targetUserId, hiddenDate).mapTo[HiddenUserModel]
}

// --- Favorite ---
class FavoritesTable(tag: Tag) extends Table[FavoriteModel](tag, "Favorite") {
  def favoriteId = column[Long]("favorite_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def contentTypeId = column[String]("content_type_id", O.Length(6))
  def contentId = column[Long]("content_id")
  def favoriteDate = column[Long]("favorite_date")(longToTimestampColumnType)

  def idx = index("UQ_user_content_fav", (userId, contentTypeId, contentId), unique = true)
  def user = foreignKey("FK_Favorite_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def contentType = foreignKey("FK_Favorite_Content_Types", contentTypeId, TableQueries.contentTypes)(
    _.contentTypeId,
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (favoriteId.?, userId, contentTypeId, contentId, favoriteDate).mapTo[FavoriteModel]
}

class FollowsTable(tag: Tag) extends Table[FollowModel](tag, "Follows") {
  def followId = column[Long]("follow_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def tagId = column[Long]("tag_id")
  def wordId = column[Option[Long]]("word_id")
  def isWordContained = column[Boolean]("is_word_contained", O.Default(false))
  def followDate = column[Long]("follow_date")(longToTimestampColumnType)

  // 生成列 (VIRTUAL) は基本マッピングに含めないか、読み取り専用にする
  def wordIdUniqHelper = column[Long]("word_id_uniq_helper", O.SqlType("BIGINT AS (IFNULL(word_id, -1)) VIRTUAL"))

  // ユニーク制約
  def idx = index("UQ_user_follow", (userId, tagId, wordIdUniqHelper), unique = true)

  def user = foreignKey("FK_Follows_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def tagRef = foreignKey("FK_Follows_Tags", tagId, TableQueries.tags)(_.tagId)
  def word = foreignKey("FK_Follows_Words", wordId, TableQueries.words)(_.wordId.?)

  def * = (followId.?, userId, tagId, wordId, isWordContained, followDate).mapTo[FollowModel]
}

// --- Search_histories ---
class SearchHistoriesTable(tag: Tag) extends Table[SearchHistoryModel](tag, "Search_histories") {
  def searchHistoryId = column[Long]("search_histry_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def searchDate = column[Long]("search_date")(longToTimestampColumnType)
  def searchHistoryText = column[Option[String]]("search_history_text", O.Length(500))

  def idx = index("IDX_user_search", (userId, searchDate)) // Slick 3.3系ではDESC指定は標準メソッドにはないため、必要ならRaw SQL
  def user = foreignKey("FK_Search_histories_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )

  def * = (searchHistoryId.?, userId, searchDate, searchHistoryText).mapTo[SearchHistoryModel]
}

// --- View_histories ---
class ViewHistoriesTable(tag: Tag) extends Table[ViewHistoryModel](tag, "View_histories") {
  def userId = column[String]("user_id", O.Length(50))
  def wordId = column[Long]("word_id")
  def tagId = column[Long]("tag_id")
  def viewDate = column[Long]("view_date")(longToTimestampColumnType)

  def pk = primaryKey("pk_view_histories", (userId, wordId, tagId))
  def user = foreignKey("FK_View_histories_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def wordTag = foreignKey("FK_View_histories_Word_Tags", (wordId, tagId), TableQueries.wordTags)(
    x => (x.wordId, x.tagId),
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (userId, wordId, tagId, viewDate).mapTo[ViewHistoryModel]
}

// --- Deleted_Users ---
class DeletedUsersTable(tag: Tag) extends Table[DeletedUserModel](tag, "Deleted_Users") {
  def deletedId = column[Long]("deleted_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def userName = column[String]("user_name", O.Length(30))
  def deleteDate = column[Long]("delete_date")(longToTimestampColumnType)
  def deleteReason = column[String]("delete_reason", O.Length(500))

  def * = (deletedId.?, userId, userName, deleteDate, deleteReason).mapTo[DeletedUserModel]
}

// --- Login_Log ---
class LoginLogsTable(tag: Tag) extends Table[LoginLogModel](tag, "Login_Log") {
  def userId = column[String]("user_id", O.Length(50))
  def loginDate = column[Long]("login_date")(longToTimestampColumnType)
  def logoutDate = column[Long]("logout_date")(longToTimestampColumnType).?

  def pk = primaryKey("pk_login_log", (userId, loginDate))
  def user = foreignKey("FK_Login_Log_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )

  def * = (userId, loginDate, logoutDate).mapTo[LoginLogModel]
}

// --- Notifications ---
class NotificationsTable(tag: Tag) extends Table[NotificationModel](tag, "Notifications") {
  def notificationsId = column[Long]("notifications_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def notificationsDate = column[Long]("notifications_date")(longToTimestampColumnType)
  def isRead = column[Boolean]("is_read", O.Default(false))
  def notificationTypesId = column[String]("notification_types_id", O.Length(6))

  def user = foreignKey("FK_Notifications_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def nType = foreignKey("FK_Notifications_Notification_Types", notificationTypesId, TableQueries.notificationTypes)(
    _.notificationTypesId,
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (notificationsId.?, userId, notificationsDate, isRead, notificationTypesId).mapTo[NotificationModel]
}

class SystemNotificationsTable(tag: Tag) extends Table[SystemNotificationModel](tag, "System_Notifications") {
  def notificationsId = column[Long]("notifications_id", O.PrimaryKey)
  def notificationsText = column[String]("notifications_text", O.Length(300))

  def notification = foreignKey("FK_System_Notifications", notificationsId, TableQueries.notifications)(
    _.notificationsId,
    onDelete = ForeignKeyAction.Cascade
  )

  def * = (notificationsId, notificationsText).mapTo[SystemNotificationModel]
}

// --- Reaction_Notifications ---
class ReactionNotificationsTable(tag: Tag) extends Table[ReactionNotificationModel](tag, "Reaction_Notifications") {
  def notificationsId = column[Long]("notifications_id", O.PrimaryKey)
  def actionUserId = column[String]("action_user_id", O.Length(50))
  def contentTypeId = column[String]("content_type_id", O.Length(6))
  def contentId = column[Long]("content_id")
  def reactionId = column[Long]("reaction_id")

  def notification = foreignKey("FK_Reaction_Notifications", notificationsId, TableQueries.notifications)(
    _.notificationsId,
    onDelete = ForeignKeyAction.Cascade
  )
  def actionUser = foreignKey("FK_Reaction_Notifications_Users", actionUserId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def contentType = foreignKey("FK_Reaction_Notifications_Content_Types", contentTypeId, TableQueries.contentTypes)(
    _.contentTypeId,
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (notificationsId, actionUserId, contentTypeId, contentId, reactionId).mapTo[ReactionNotificationModel]
}

// --- Follow_Notifications ---
class FollowNotificationsTable(tag: Tag) extends Table[FollowNotificationModel](tag, "Follow_Notifications") {
  def notificationsId = column[Long]("notifications_id", O.PrimaryKey)
  def contentTypeId = column[String]("content_type_id", O.Length(6))
  def contentId = column[Long]("content_id")
  def followType = column[String]("follow_type") // Enum('TAG', 'WORD')

  def notification = foreignKey("FK_Follow_Notifications", notificationsId, TableQueries.notifications)(
    _.notificationsId,
    onDelete = ForeignKeyAction.Cascade
  )
  def contentType = foreignKey("FK_Follow_Notifications_Content_Types", contentTypeId, TableQueries.contentTypes)(
    _.contentTypeId,
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (notificationsId, contentTypeId, contentId, followType).mapTo[FollowNotificationModel]
}

// --- Comment_Notifications ---
class CommentNotificationsTable(tag: Tag) extends Table[CommentNotificationModel](tag, "Comment_Notifications") {
  def notificationsId = column[Long]("notifications_id", O.PrimaryKey)
  def threadId = column[Long]("thread_id")
  def replyCommentId = column[Long]("reply_comment_id")
  def replyToCommentId = column[Option[Long]]("reply_to_comment_id")
  def notificationKind = column[String]("notification_kind", O.Length(32), O.Default("COMMENT"))

  def notification = foreignKey("FK_Comment_Notifications", notificationsId, TableQueries.notifications)(
    _.notificationsId,
    onDelete = ForeignKeyAction.Cascade
  )
  def thread = foreignKey("FK_Comment_Notifications_Threads", threadId, TableQueries.threads)(_.threadId)
  def replyComment =
    foreignKey("FK_Comment_Notifications_Reply_Comment", replyCommentId, TableQueries.comments)(_.commentId)
  def replyToComment =
    foreignKey("FK_Comment_Notifications_To_Comment", replyToCommentId, TableQueries.comments)(_.commentId.?)

  def * = (notificationsId, threadId, replyCommentId, replyToCommentId, notificationKind)
    .mapTo[CommentNotificationModel]
}

class PushSubscriptionsTable(tag: Tag) extends Table[PushSubscriptionModel](tag, "Push_Subscriptions") {
  def subscriptionId = column[Long]("subscription_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def endpoint = column[String]("endpoint", O.Length(2048))
  def endpointHash = column[String]("endpoint_hash", O.Length(64))
  def p256dhKey = column[String]("p256dh_key", O.Length(255))
  def authKey = column[String]("auth_key", O.Length(255))
  def userAgent = column[Option[String]]("user_agent", O.Length(500))
  def createdAt = column[Long]("created_at")(longToTimestampColumnType)
  def lastModified = column[Long]("last_modified")(longToTimestampColumnType)
  def failureCount = column[Int]("failure_count", O.Default(0))
  def lastFailureAt = column[Option[Long]]("last_failure_at")(longToTimestampColumnType.optionType)
  def isDeleted = column[Boolean]("is_deleted", O.Default(false))
  def deletedAt = column[Option[Long]]("deleted_at")(longToTimestampColumnType.optionType)

  def endpointHashIndex = index("UQ_Push_Subscriptions_endpoint_hash", endpointHash, unique = true)
  def userActiveIndex = index("IX_Push_Subscriptions_user_active", (userId, isDeleted))
  def user = foreignKey("FK_Push_Subscriptions_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )

  def * = (
    subscriptionId.?,
    userId,
    endpoint,
    endpointHash,
    p256dhKey,
    authKey,
    userAgent,
    createdAt,
    lastModified,
    failureCount,
    lastFailureAt,
    isDeleted,
    deletedAt
  ).mapTo[PushSubscriptionModel]
}

// --- Reports ---
class ReportsTable(tag: Tag) extends Table[ReportModel](tag, "Reports") {
  def reportId = column[Long]("report_id", O.PrimaryKey, O.AutoInc)
  def userId = column[String]("user_id", O.Length(50))
  def reportTypeId = column[String]("report_type_id", O.Length(6))
  def reportStatusId = column[String]("report_status_id", O.Length(6))
  def contentTypeId = column[String]("content_type_id", O.Length(6))
  def contentId = column[Long]("content_id")
  def reportDate = column[Long]("report_date")(longToTimestampColumnType)

  def user = foreignKey("FK_Reports_Users", userId, TableQueries.users)(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )
  def rType = foreignKey("FK_Reports_Report_Types", reportTypeId, TableQueries.reportTypes)(
    _.reportTypeId,
    onUpdate = ForeignKeyAction.Cascade
  )
  def rStatus = foreignKey("FK_Reports_Report_Status", reportStatusId, TableQueries.reportStatuses)(
    _.reportStatusId,
    onUpdate = ForeignKeyAction.Cascade
  )
  def cType = foreignKey("FK_Reports_Content_Types", contentTypeId, TableQueries.contentTypes)(
    _.contentTypeId,
    onUpdate = ForeignKeyAction.Cascade
  )

  def * = (reportId.?, userId, reportTypeId, reportStatusId, contentTypeId, contentId, reportDate).mapTo[ReportModel]
}

object TableQueries {
  val users = TableQuery[UsersTable]
  val roles = TableQuery[RolesTable]
  val words = TableQuery[WordsTable]
  val tags = TableQuery[TagsTable]
  val threadTypes = TableQuery[ThreadTypesTable]
  val contentTypes = TableQuery[ContentTypesTable]
  val reportTypes = TableQuery[ReportTypesTable]
  val reportStatuses = TableQuery[ReportStatusTable]
  val notificationTypes = TableQuery[NotificationTypesTable]
  val wordTags = TableQuery[WordTagsTable]
  val meanings = TableQuery[MeaningsTable]
  val examples = TableQuery[ExamplesTable]
  val trendHot = TableQuery[TrendHotTable]
  val trendNew = TableQuery[TrendNewTable]
  val trendBuzz = TableQuery[TrendBuzzTable]
  val reactionGood = TableQuery[ReactionGoodTable]
  val reactionHelpful = TableQuery[ReactionHelpfulTable]
  val reactionFunny = TableQuery[ReactionFunnyTable]
  val threads = TableQuery[ThreadsTable]
  val comments = TableQuery[CommentsTable]
  val contentQuotes = TableQuery[ContentQuotesTable]
  val threadQuestions = TableQuery[ThreadQuestionsTable]
  val commentReactions = TableQuery[CommentReactionsTable]
  val hiddenUsers = TableQuery[HiddenUsersTable]
  val favorites = TableQuery[FavoritesTable]
  val follows = TableQuery[FollowsTable]
  val searchHistories = TableQuery[SearchHistoriesTable]
  val viewHistories = TableQuery[ViewHistoriesTable]
  val deletedUsers = TableQuery[DeletedUsersTable]
  val loginLogs = TableQuery[LoginLogsTable]
  val notifications = TableQuery[NotificationsTable]
  val systemNotifications = TableQuery[SystemNotificationsTable]
  val reactionNotifications = TableQuery[ReactionNotificationsTable]
  val followNotifications = TableQuery[FollowNotificationsTable]
  val commentNotifications = TableQuery[CommentNotificationsTable]
  val pushSubscriptions = TableQuery[PushSubscriptionsTable]
  val reports = TableQuery[ReportsTable]

}
