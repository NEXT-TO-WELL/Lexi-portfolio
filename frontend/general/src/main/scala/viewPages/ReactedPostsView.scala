package viewPages
import general.ui.PullToRefresh

import com.raquo.laminar.api.L.*
import general.mypage.{MypageSavedPostFilters, MypageSavedPostsApi, ReactionFilter}
import general.mypage.MyPageWordItem
import models.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

object ReactedPostsView {
  sealed trait ReactedTab
  case object MeaningTab extends ReactedTab
  case object ExampleTab extends ReactedTab

  val AllReactions = ReactionFilter.All
  val GoodReaction = ReactionFilter.Good
  val HelpfulReaction = ReactionFilter.Helpful
  val FunnyReaction = ReactionFilter.Funny

  private val activeTab = Var[ReactedTab](MeaningTab)
  private val activeFilter = Var[ReactionFilter](AllReactions)
  private val reactedMeanings = Var(Seq.empty[MyPageMeaningViewValue])
  private val reactedExamples = Var(Seq.empty[MyPageExampleViewValue])
  private val isFetching = Var(false)
  private val searchQuery = Var("")
  private var reactedMeaningsLoaded = false
  private var reactedExamplesLoaded = false

  def apply(): HtmlElement = {
    div(
      cls := "w-full h-full min-h-0 flex flex-col bg-gray-50 dark:bg-gray-950 overflow-hidden",
      div(cls := "flex-none h-14"),

      div(
        cls := "flex-none bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 z-20 shadow-sm",
        div(
          cls := "h-0.5 bg-blue-500 transition-all duration-300",
          width <-- isFetching.signal.map(if (_) "100%" else "0%"),
          opacity <-- isFetching.signal.map(if (_) "1" else "0")
        ),
        div(cls := "flex", renderTabButton("語釈", MeaningTab), renderTabButton("用例", ExampleTab)),
        div(
          cls := "px-4 py-3 space-y-3 border-t border-gray-100 dark:border-gray-800",
          renderFilterButtons(),
          MypageSavedPostFilters.searchBar(searchQuery, "リアクションした投稿を検索...")
        )
      ),

      PullToRefresh(() => refreshCurrentTab())(
        div(
          cls := "p-4 pb-6",
          child <-- activeTab.signal
            .combineWith(
              isFetching.signal,
              reactedMeanings.signal,
              reactedExamples.signal,
              searchQuery.signal,
              activeFilter.signal
            )
            .map { case (tab, loading, meanings, examples, query, filter) =>
              if (loading) {
                MypageSavedPostFilters.loading()
              } else {
                tab match {
                  case MeaningTab =>
                    MypageSavedPostFilters.list(
                      MypageSavedPostFilters.filterMeanings(meanings, query, Some(filter)),
                      query,
                      "リアクションした語釈はまだありません"
                    ) { m =>
                      MyPageWordItem.meaningCard(m)
                    }
                  case ExampleTab =>
                    MypageSavedPostFilters.list(
                      MypageSavedPostFilters.filterExamples(examples, query, Some(filter)),
                      query,
                      "リアクションした用例はまだありません"
                    ) { e =>
                      MyPageWordItem.exampleCard(e)
                    }
                }
              }
            }
        )
      ),

      onMountCallback { _ =>
        if (!reactedMeaningsLoaded) fetchReactedMeanings()
      }
    )
  }

  private def refreshCurrentTab(): Unit = {
    activeTab.now() match {
      case MeaningTab => fetchReactedMeanings()
      case ExampleTab => fetchReactedExamples()
    }
  }

  private def renderTabButton(label: String, tab: ReactedTab): HtmlElement = {
    button(
      cls <-- activeTab.signal.map(t =>
        s"flex-1 py-4 text-sm font-bold border-b-2 transition-all ${
            if (t == tab) "border-blue-600 text-blue-600 dark:text-blue-400"
            else "border-transparent text-gray-400 dark:text-gray-500"
          }"
      ),
      onClick --> { _ =>
        if (!isFetching.now()) {
          activeTab.set(tab)
          tab match {
            case MeaningTab => if (!reactedMeaningsLoaded) fetchReactedMeanings()
            case ExampleTab => if (!reactedExamplesLoaded) fetchReactedExamples()
          }
        }
      },
      label
    )
  }

  private def renderFilterButtons(): HtmlElement = {
    div(
      cls := "grid grid-cols-4 gap-1 rounded-xl bg-gray-100 dark:bg-gray-800 p-1",
      renderFilterButton("すべて", AllReactions),
      renderFilterButton("Good", GoodReaction),
      renderFilterButton("Helpful", HelpfulReaction),
      renderFilterButton("Funny", FunnyReaction)
    )
  }

  private def renderFilterButton(label: String, filter: ReactionFilter): HtmlElement = {
    button(
      cls <-- activeFilter.signal.map { current =>
        val active = current == filter
        s"rounded-lg px-2 py-2 text-[11px] font-bold transition ${
            if (active) "bg-white dark:bg-gray-700 text-blue-600 dark:text-blue-300 shadow-sm"
            else "text-gray-500 dark:text-gray-400"
          }"
      },
      onClick --> { _ => activeFilter.set(filter) },
      label
    )
  }

  private def fetchReactedMeanings(): Unit =
    performFetch(MypageSavedPostsApi.fetchReactedMeanings(), reactedMeanings.set, () => reactedMeaningsLoaded = true)
  private def fetchReactedExamples(): Unit =
    performFetch(MypageSavedPostsApi.fetchReactedExamples(), reactedExamples.set, () => reactedExamplesLoaded = true)

  private def performFetch[A](
      request: scala.concurrent.Future[Seq[A]],
      update: Seq[A] => Unit,
      markLoaded: () => Unit
  ): Unit = {
    isFetching.set(true)
    request.onComplete { result =>
      isFetching.set(false)
      markLoaded()
      result match {
        case scala.util.Success(items) => update(items)
        case scala.util.Failure(e)     =>
          dom.console.error(s"Fetch reacted posts failed: ${e.getMessage}")
          update(Nil)
      }
    }
  }
}
