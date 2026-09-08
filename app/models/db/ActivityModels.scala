package models.db

import upickle.default.ReadWriter

case class FavoriteModel(
    favoriteId: Option[Long],
    userId: String,
    contentTypeId: String,
    contentId: Long,
    favoriteDate: Long
) derives ReadWriter

case class FollowModel(
    followId: Option[Long],
    userId: String,
    tagId: Long,
    wordId: Option[Long],
    isWordContained: Boolean,
    followDate: Long
) derives ReadWriter

case class SearchHistoryModel(
    searchHistoryId: Option[Long],
    userId: String,
    searchDate: Long,
    searchHistoryText: Option[String]
) derives ReadWriter

case class ViewHistoryModel(userId: String, wordId: Long, tagId: Long, viewDate: Long) derives ReadWriter
