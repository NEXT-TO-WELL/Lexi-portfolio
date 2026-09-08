package general.word

import models.ThreadViewValue
import shared.api.{ExampleViewValue, MeaningViewValue}

object WordDetailFilters {
  sealed trait SortType
  case object SortByDateDesc extends SortType
  case object SortByDateAsc extends SortType
  case object SortByGood extends SortType
  case object SortByHelpful extends SortType
  case object SortByFunny extends SortType
  case object SortByCommentCount extends SortType

  sealed trait QuestionFilter
  case object ShowAll extends QuestionFilter
  case object ShowResolved extends QuestionFilter
  case object ShowUnresolved extends QuestionFilter

  sealed trait ThreadTypeFilter
  case object AllTypes extends ThreadTypeFilter
  case object TypeQuestion extends ThreadTypeFilter
  case object TypeDiscussion extends ThreadTypeFilter
  case object TypeChat extends ThreadTypeFilter
  case object TypeShare extends ThreadTypeFilter

  def sortMeanings(items: Seq[MeaningViewValue], sortType: SortType): Seq[MeaningViewValue] =
    sortType match {
      case SortByDateDesc     => items.sortBy(-_.meaning.meaningPostedDate)
      case SortByDateAsc      => items.sortBy(_.meaning.meaningPostedDate)
      case SortByGood         => items.sortBy(-_.meaning.meaningGoodCount)
      case SortByHelpful      => items.sortBy(-_.meaning.meaningHelpfulCount)
      case SortByFunny        => items.sortBy(-_.meaning.meaningFunnyCount)
      case SortByCommentCount => items
    }

  def sortExamples(items: Seq[ExampleViewValue], sortType: SortType): Seq[ExampleViewValue] =
    sortType match {
      case SortByDateDesc     => items.sortBy(-_.example.examplePostedDate)
      case SortByDateAsc      => items.sortBy(_.example.examplePostedDate)
      case SortByGood         => items.sortBy(-_.example.exampleGoodCount)
      case SortByHelpful      => items.sortBy(-_.example.exampleHelpfulCount)
      case SortByFunny        => items.sortBy(-_.example.exampleFunnyCount)
      case SortByCommentCount => items
    }

  def sortThreads(items: Seq[ThreadViewValue], sortType: SortType): Seq[ThreadViewValue] =
    sortType match {
      case SortByDateDesc     => items.sortBy(-_.threadCreateDate)
      case SortByDateAsc      => items.sortBy(_.threadCreateDate)
      case SortByCommentCount => items.sortBy(-_.commentCount)
      case _                  => items.sortBy(-_.threadCreateDate)
    }

  def filterThreads(items: Seq[ThreadViewValue], filter: QuestionFilter): Seq[ThreadViewValue] =
    filter match {
      case ShowAll        => items
      case ShowResolved   => items.filter(_.questionStatus.exists(_.isResolved))
      case ShowUnresolved => items.filter(_.questionStatus.exists(status => !status.isResolved))
    }

  def filterThreadsByType(items: Seq[ThreadViewValue], filter: ThreadTypeFilter): Seq[ThreadViewValue] =
    filter match {
      case AllTypes       => items
      case TypeQuestion   => items.filter(_.threadType.threadTypeId == "2")
      case TypeDiscussion => items.filter(_.threadType.threadTypeId == "1")
      case TypeChat       => items.filter(_.threadType.threadTypeId == "3")
      case TypeShare      => items.filter(_.threadType.threadTypeId == "4")
    }
}
