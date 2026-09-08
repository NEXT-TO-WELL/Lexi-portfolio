package shared.api

import upickle.default.ReadWriter

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

case class MeaningViewValue(
  meaning: MeaningModel,
  userName: String,
  displayId: Option[String],
  isGood: Boolean = false,
  isHelpful: Boolean = false,
  isFunny: Boolean = false,
  isFavorite: Boolean = false
) derives ReadWriter

case class ExampleViewValue(
  example: ExampleModel,
  userName: String,
  displayId: Option[String],
  isGood: Boolean = false,
  isHelpful: Boolean = false,
  isFunny: Boolean = false,
  isFavorite: Boolean = false
) derives ReadWriter
