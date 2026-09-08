package models

import upickle.default.ReadWriter

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
case class TrendResponse(
    hot: Seq[TrendViewValue],
    news: Seq[TrendViewValue],
    buzz: Seq[TrendViewValue],
    random: Seq[TrendViewValue]
) derives ReadWriter
