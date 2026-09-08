package models

object ApiModelConversions {
  def meaning(value: MeaningModel): shared.api.MeaningModel =
    shared.api.MeaningModel(
      value.meaningId,
      value.wordId,
      value.tagId,
      value.userId,
      value.meaningPostedDate,
      value.meaningText,
      value.isJoke,
      value.meaningGoodCount,
      value.meaningHelpfulCount,
      value.meaningFunnyCount,
      value.isDeleted,
      value.lastModified
    )

  def example(value: ExampleModel): shared.api.ExampleModel =
    shared.api.ExampleModel(
      value.exampleId,
      value.wordId,
      value.tagId,
      value.userId,
      value.examplePostedDate,
      value.exampleText,
      value.isJoke,
      value.exampleGoodCount,
      value.exampleHelpfulCount,
      value.exampleFunnyCount,
      value.isDeleted,
      value.lastModified
    )
}
