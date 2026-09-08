package general.word

import com.raquo.laminar.api.L.Var
import general.word.WordDetailFilters.*
import models.{ThreadViewValue, WordDetailTab, WordTagDetailResponse}
import shared.api.{ContentQuoteViewValue, ExampleViewValue, MeaningViewValue}

final class WordDetailState {
  val headerVar = Var(Option.empty[WordTagDetailResponse])
  val meaningsVar = Var(Seq.empty[MeaningViewValue])
  val examplesVar = Var(Seq.empty[ExampleViewValue])
  val threadsVar = Var(Seq.empty[ThreadViewValue])
  val selectedThreadQuoteVar = Var(Option.empty[ContentQuoteViewValue])

  val activeTab = Var[WordDetailTab](WordDetailTab.Meaning)
  val showJokesVar = Var(true)
  val sortTypeVar = Var[SortType](SortByDateDesc)
  val questionFilterVar = Var[QuestionFilter](ShowAll)
  val threadTypeFilterVar = Var[ThreadTypeFilter](AllTypes)

  val isFollowedVar = Var(false)
  val meaningsLoadingVar = Var(false)
  val meaningsHasMoreVar = Var(true)
  val examplesLoadingVar = Var(false)
  val examplesHasMoreVar = Var(true)
  val threadsLoadingVar = Var(false)
  val threadsHasMoreVar = Var(true)

  val isShowingMeaningModal = Var(false)
  val isShowingExampleModal = Var(false)
  val isShowingThreadForm = Var(false)

  private var loadedDetailKey = Option.empty[(Long, Long)]
  private var headerLoadedFlag = false
  private var meaningsLoadedFlag = false
  private var examplesLoadedFlag = false
  private var threadsLoadedFlag = false

  def prepare(wordId: Long, tagId: Long, initialTab: WordDetailTab): Unit = {
    activeTab.set(initialTab)
    val detailKey = (wordId, tagId)

    if (!loadedDetailKey.contains(detailKey)) {
      loadedDetailKey = Some(detailKey)
      headerLoadedFlag = false
      meaningsLoadedFlag = false
      examplesLoadedFlag = false
      threadsLoadedFlag = false
      headerVar.set(None)
      meaningsVar.set(Nil)
      examplesVar.set(Nil)
      threadsVar.set(Nil)
      selectedThreadQuoteVar.set(None)
      isFollowedVar.set(false)
      meaningsLoadingVar.set(false)
      meaningsHasMoreVar.set(true)
      examplesLoadingVar.set(false)
      examplesHasMoreVar.set(true)
      threadsLoadingVar.set(false)
      threadsHasMoreVar.set(true)
    }
  }

  def headerLoaded: Boolean = headerLoadedFlag
  def meaningsLoaded: Boolean = meaningsLoadedFlag
  def examplesLoaded: Boolean = examplesLoadedFlag
  def threadsLoaded: Boolean = threadsLoadedFlag

  def markHeaderLoaded(): Unit = headerLoadedFlag = true
  def markMeaningsLoaded(): Unit = meaningsLoadedFlag = true
  def markExamplesLoaded(): Unit = examplesLoadedFlag = true
  def markThreadsLoaded(): Unit = threadsLoadedFlag = true
}
