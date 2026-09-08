package models

import upickle.default._
import shared.api.ContentQuoteReference

// 新規投稿保存リクエスト用モデル
case class PostEntry(text: String, isJoke: Boolean) derives ReadWriter

case class WordTagPostRequest(
    wordId: Option[Long], // Noneなら新規の言葉
    wordMain: String, // 言葉本体
    wordReading: String, // 読み
    tagId: Option[Long], // Noneなら新規のタグ
    tagName: String, // タグ名（1つ）
    meaning: Option[PostEntry],
    example: Option[PostEntry]
) derives ReadWriter

case class WordTagPostResponse(
    wordId: Long,
    tagId: Long,
    wordMain: String,
    tagName: String,
    hasMeaning: Boolean,
    hasExample: Boolean
) derives ReadWriter

case class MeaningPostRequest(
    wordId: Long,
    tagId: Long,
    meaningText: String,
    isJoke: Boolean
) derives ReadWriter

case class ExamplePostRequest(
    wordId: Long,
    tagId: Long,
    exampleText: String,
    isJoke: Boolean
) derives ReadWriter

// サジェストを取得するためのリクエスト
case class SuggestionRequest(
    query: String, // 入力中の文字列（空文字の場合はフォーカス時と判定）
    relatedWordId: Option[Long] = None, // 言葉が既に入力・選択済みの場合、そのID
    relatedTagId: Option[Long] = None // タグが既に入力・選択済みの場合、そのID
) derives ReadWriter

// サジェストの1項目を表すモデル
case class SuggestionItem(
    id: Long, // 言葉またはタグのID
    name: String, // 表示名（言葉名やタグ名）
    reading: Option[String], // 読み（特に言葉の場合、選択時に自動入力するために保持）
    sourceType: String // 出現理由の識別子 ("related", "search" など)
) derives ReadWriter

// suggestレスポンス全体（必要に応じてメタデータを含める）
case class SuggestionResponse(
    items: Seq[SuggestionItem], // 検索結果が0の時(新規の言葉、タグの時)、空のリストを返す
    query: String // 入力文字（空文字ならフォーカス時の関連表示）
) derives ReadWriter

// 言葉の判定結果を受け取るモデル
case class WordExistsResponse(
    exists: Boolean, // 存在するかどうか
    wordId: Option[Long] // 存在する場合のID
) derives ReadWriter

// タグの判定結果を受け取るモデル
case class TagExistsResponse(
    exists: Boolean, // 存在するかどうか
    tagId: Option[Long] // 存在する場合のID
) derives ReadWriter

// 言葉×タグの「組み合わせ」判定結果
case class WordTagPairExistsResponse(
    // 1. 言葉とタグそれぞれの存在状態
    wordId: Option[Long], // 言葉がDBに存在すればIDが入る
    tagId: Option[Long], // タグがDBに存在すればIDが入る

    // 2. 組み合わせ（ペア）としての状態
    isExistingPair: Boolean, // その言葉にそのタグが既に紐付いているか

    // 3. 既存ペアの場合の情報
    // 読みは「言葉×タグ」に紐付くため、ペアが存在する場合のみ返る
    existingReading: Option[String] = None
) derives ReadWriter

// 共通エラーレスポンス
case class ThreadPostRequest(
    wordId: Long,
    tagId: Long,
    threadTypeId: String,
    threadTitle: String,
    quote: Option[ContentQuoteReference] = None
) derives ReadWriter

case class ThreadUpdateRequest(threadTitle: String) derives ReadWriter

// 質問スレッドの解決処理
case class ResolveThreadRequest(
    threadId: Long,
    bestCommentId: Long
) derives ReadWriter

case class CommentPostRequest(
    threadId: Long, // どのスレッドに対するコメントか
    parentCommentId: Option[Long], // 返信（ツリー構造）の場合は親コメントのID
    commentText: String, // コメント本文
    quote: Option[ContentQuoteReference] = None
) derives ReadWriter

case class CommentUpdateByTextRequest(commentText: String) derives ReadWriter

case class FavoriteToggleRequest(
    contentTypeId: String, // "1", "2", "3"
    contentId: Long
) derives ReadWriter

case class FavoriteToggleResponse(
    isFavorite: Boolean
) derives ReadWriter

case class SearchSuggestRequest(
    query: String, // 2つ目の入力（スペースより後の文字）

    // 1つ目の要素の特定用（IDがあればIDを、なければ文字列を送る）
    baseWordId: Option[Long] = None,
    baseWordName: Option[String] = None,

    baseTagId: Option[Long] = None,
    baseTagName: Option[String] = None
) derives ReadWriter

// 検索サジェスト専用モデル
case class SearchSuggestionItem(
    id: Long, // 今回の候補のID
    name: String, // 表示名
    reading: Option[String], // 読み
    sourceType: String, // "word", "tag"

    // 複合検索のための情報
    baseId: Option[Long] = None, // 1つ目に確定したID（あれば）
    baseType: Option[String] = None // 1つ目の型 ("word", "tag")
) derives ReadWriter

case class SearchSuggestionResponse(
    items: Seq[SearchSuggestionItem],
    query: String
) derives ReadWriter

case class ReportRequest(
    reportTypeId: String,
    contentTypeId: String,
    contentId: Long
) derives ReadWriter
