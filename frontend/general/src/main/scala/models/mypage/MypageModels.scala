package models

import upickle.default.ReadWriter
import shared.api.{ExampleViewValue, MeaningViewValue}

case class MyProfileResponse(
    userId: String,
    displayId: String,
    userName: String,
    totalPosts: Int,
    totalThreads: Int,
    totalComments: Int,
    totalFavorites: Int
) derives ReadWriter

case class MyPageMeaningViewValue(
    meaningView: MeaningViewValue,
    wordMain: String,
    tagName: String,
    favoriteId: Option[Long] = None
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

case class FollowItemViewValue(
    followId: Long,
    tagId: Long,
    tagName: String,
    wordId: Option[Long],
    wordMain: Option[String],
    followDate: Long,
    isWordContained: Boolean
) derives ReadWriter

case class TagFollowsResponse(tagFollows: Seq[FollowItemViewValue]) derives ReadWriter

case class WordFollowsResponse(wordTagFollows: Seq[FollowItemViewValue]) derives ReadWriter
