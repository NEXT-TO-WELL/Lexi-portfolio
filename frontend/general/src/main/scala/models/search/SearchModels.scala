package models

import upickle.default.ReadWriter

case class SearchViewValue(wordId: Long, tagId: Long, wordMain: String, tagName: String, wordReading: String)
    derives ReadWriter
case class SearchResponse(results: Seq[SearchViewValue]) derives ReadWriter

case class SearchHistoryModel(
    searchHistoryId: Option[Long],
    userId: String,
    searchDate: Long,
    searchHistoryText: Option[String]
) derives ReadWriter
case class SearchHistoryViewValue(searchHistoryId: Long, searchHistoryText: String) derives ReadWriter
case class SearchHistoryResponse(histories: List[SearchHistoryViewValue]) derives ReadWriter

case class ViewHistoryModel(userId: String, wordId: Long, tagId: Long, viewDate: Long) derives ReadWriter
case class ViewHistoryViewValue(
    wordId: Long,
    tagId: Long,
    wordMain: String,
    tagName: String,
    wordReading: String,
    viewDate: Long
) derives ReadWriter
case class ViewHistoryResponse(viewHistories: Seq[ViewHistoryViewValue]) derives ReadWriter
