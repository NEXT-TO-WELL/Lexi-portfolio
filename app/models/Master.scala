package models

import upickle.default.ReadWriter.join

import upickle.default.ReadWriter
import shared.api.{ContentQuoteViewValue, ExampleViewValue, MeaningViewValue}

case class AdminUserView(
    userId: String,
    displayId: String,
    userName: String,
    roleId: String,
    userRegistrationDate: Option[Long]
) derives ReadWriter

case class ThreadType(threadTypeId: String, threadTypeName: String) derives ReadWriter

// 検索結果表示用
case class SearchViewValue(
    wordId: Long,
    tagId: Long,
    wordMain: String,
    tagName: String,
    wordReading: String
) derives ReadWriter

// 検索結果ページのレスポンス
case class SearchResponse(
    results: Seq[SearchViewValue]
) derives ReadWriter

case class MeaningModel(
    meaningId: Option[Long],
    wordId: Long,
    tagId: Long,
    userId: Option[String],
    meaningPostedDate: Long,
    meaningText: String,
    isJoke: Boolean,
    meaningGoodCount: Int,
    meaningHelpfulCount: Int,
    meaningFunnyCount: Int,
    isDeleted: Boolean,
    lastModified: Long
) derives ReadWriter

case class ExampleModel(
    exampleId: Option[Long],
    wordId: Long,
    tagId: Long,
    userId: Option[String],
    examplePostedDate: Long,
    exampleText: String,
    isJoke: Boolean,
    exampleGoodCount: Int,
    exampleHelpfulCount: Int,
    exampleFunnyCount: Int,
    isDeleted: Boolean,
    lastModified: Long
) derives ReadWriter

// 意味表示用
case class TrendHotModel(hotRank: Int, wordId: Long, tagId: Long, commentCount: Int) derives ReadWriter

case class TrendNewModel(newRank: Int, wordId: Long, tagId: Long, goodCount: Int) derives ReadWriter

case class TrendBuzzModel(buzzRank: Int, wordId: Long, tagId: Long, goodCount: Int) derives ReadWriter

case class TrendViewValue(
    rank: Int,
    wordId: Long,
    wordName: String,
    tagId: Long,
    tagName: String,
    count: Int,
    trendType: String
) derives ReadWriter
// 表示用のきれいな型を定義
case class TrendResponse(
    hot: Seq[TrendViewValue],
    news: Seq[TrendViewValue],
    buzz: Seq[TrendViewValue],
    random: Seq[TrendViewValue]
) derives ReadWriter

// 質問スレッドのステータス表示
case class QuestionStatusViewValue(
    isResolved: Boolean,
    resolvedDate: Option[String],
    bestCommentId: Option[Long]
) derives ReadWriter

// スレッド基本情報表示用（一覧・詳細共通）
case class ThreadViewValue(
    threadId: Long,
    userId: Option[String], // ← 内部用：オーナーシップ確認に使用
    displayId: Option[String], // ← 公開用：画面表示に使用
    userName: String,
    threadTitle: String,
    threadCreateDate: Long,
    threadType: ThreadType,
    wordId: Long,
    tagId: Long,
    // 質問スレッドの場合のみデータが入り、それ以外は None
    questionStatus: Option[QuestionStatusViewValue] = None,
    commentCount: Int,
    quote: Option[ContentQuoteViewValue] = None
) derives ReadWriter

// --- 言葉詳細画面基本情報のレスポンス ---
case class WordTagDetailResponse(
    wordId: Long,
    wordMain: String,
    tagId: Long,
    tagName: String,
    wordReading: String,
    isFollowed: Boolean
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

// コメント本体 + 投稿者名 表示用
case class CommentViewValue(
    comment: CommentModel,
    userName: String,
    displayId: Option[String], // ← 追加：公開用ID
    isBestAnswer: Boolean, // 追加：親スレッドの bestCommentId と一致するか Backend で判定
    isGood: Boolean = false,
    isBad: Boolean = false,
    quote: Option[ContentQuoteViewValue] = None
) derives ReadWriter

// ---　スレッド詳細画面に必要なレスポンス　---
case class ThreadDetailResponse(
    thread: ThreadViewValue,
    comments: Seq[CommentViewValue]
) derives ReadWriter

// ---　マイページ画面に必要なレスポンス　---
case class MyProfileResponse(
    userId: String, // 内部ID（非表示）
    displayId: String, // 公開用ID
    userName: String,
    totalPosts: Int, // 意味用例投稿総数
    totalThreads: Int, // 作成スレッド総数
    totalComments: Int, // コメント総数
    totalFavorites: Int // お気に入り総数
) derives ReadWriter

// マイページ（自分の投稿・お気に入り）共通で使う表示用モデル
case class MyPageMeaningViewValue(
    meaningView: MeaningViewValue, // 既存の構造をそのまま再利用
    wordMain: String, // マイページで必須の単語名
    tagName: String, // マイページで必須のタグ名
    favoriteId: Option[Long] = None // お気に入り解除(DELETE)に使うID
) derives ReadWriter

case class MyPageExampleViewValue(
    exampleView: ExampleViewValue,
    wordMain: String,
    tagName: String,
    favoriteId: Option[Long] = None
) derives ReadWriter

case class MyPageWordTagViewValue(
    wordId: Long,
    tagId: Long,
    wordMain: String,
    tagName: String,
    wordReading: String,
    wordCreateDate: Long
) derives ReadWriter

case class HiddenUserModel(
    userId: String,
    targetUserId: String,
    hiddenDate: Long
) derives ReadWriter

// フォロー項目表示用
case class FollowItemViewValue(
    followId: Long,
    tagId: Long,
    tagName: String,
    wordId: Option[Long], // タグのみの場合は None
    wordMain: Option[String], // タグのみの場合は None
    followDate: Long,
    isWordContained: Boolean
) derives ReadWriter

// ---　フォロー表示画面に必要なレスポンス　---
case class TagFollowsResponse(
    tagFollows: Seq[FollowItemViewValue] // タグ
) derives ReadWriter

case class WordFollowsResponse(
    wordTagFollows: Seq[FollowItemViewValue] // タグ＋言葉 (is_word_contained = true)
) derives ReadWriter

// 検索バーの下表示するモデル
case class SearchHistoryViewValue(
    searchHistoryId: Long, // 削除操作などに使用
    searchHistoryText: String // 検索ワード本体
) derives ReadWriter

// レスポンス用
case class SearchHistoryResponse(
    histories: List[SearchHistoryViewValue]
) derives ReadWriter

// 閲覧履歴表示用
case class ViewHistoryViewValue(
    wordId: Long,
    tagId: Long,
    wordMain: String,
    tagName: String,
    wordReading: String,
    viewDate: Long
) derives ReadWriter

// ---　閲覧履歴画面に必要なレスポンス　---
case class ViewHistoryResponse(
    viewHistories: Seq[ViewHistoryViewValue]
) derives ReadWriter

case class ReportModel(
    reportId: Option[Long],
    userId: String,
    reportTypeId: String,
    reportStatusId: String,
    contentTypeId: String,
    contentId: Long,
    reportDate: Long
) derives ReadWriter

case class ReportTargetPreview(
    title: String,
    previewText: String,
    url: Option[String],
    exists: Boolean
) derives ReadWriter

case class AdminReportView(
    report: ReportModel,
    target: ReportTargetPreview
) derives ReadWriter
