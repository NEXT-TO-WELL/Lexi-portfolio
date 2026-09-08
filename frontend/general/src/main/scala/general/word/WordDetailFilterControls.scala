package general.word

import com.raquo.laminar.api.L.*
import general.word.WordDetailFilters.*
import models.WordDetailTab

object WordDetailFilterControls {
  def apply(
      activeTab: Signal[WordDetailTab],
      showJokes: Var[Boolean],
      sortType: Var[SortType],
      questionFilter: Var[QuestionFilter],
      threadTypeFilter: Var[ThreadTypeFilter]
  ): HtmlElement =
    div(
      cls := "flex flex-col sm:flex-row items-start sm:items-center sm:justify-between gap-3 px-1 pt-2",
      child <-- activeTab.map {
        case WordDetailTab.Thread => threadSortDropdown(sortType)
        case _                    => meaningExampleSortDropdown(sortType)
      },
      child <-- activeTab.map {
        case WordDetailTab.Thread => threadFilter(questionFilter, threadTypeFilter)
        case _                    => jokeToggle(showJokes)
      }
    )

  private def meaningExampleSortDropdown(sortType: Var[SortType]): HtmlElement =
    div(
      cls := "flex items-center space-x-2",
      span(cls := "text-xs font-semibold text-gray-500 dark:text-gray-400", "並び替え:"),
      select(
        cls := "text-xs px-2 py-1 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 outline-none",
        value <-- sortType.signal.map {
          case SortByDateDesc => "date-desc"
          case SortByDateAsc  => "date-asc"
          case SortByGood     => "good"
          case SortByHelpful  => "helpful"
          case SortByFunny    => "funny"
          case _              => "date-desc"
        },
        onChange.mapToValue.map {
          case "date-desc" => SortByDateDesc
          case "date-asc"  => SortByDateAsc
          case "good"      => SortByGood
          case "helpful"   => SortByHelpful
          case "funny"     => SortByFunny
          case _           => SortByDateDesc
        } --> sortType,
        option(value := "date-desc", "投稿日時（新しい順）"),
        option(value := "date-asc", "投稿日時（古い順）"),
        option(value := "good", "いいね数"),
        option(value := "helpful", "参考になった数"),
        option(value := "funny", "面白い数")
      )
    )

  private def threadSortDropdown(sortType: Var[SortType]): HtmlElement =
    div(
      cls := "flex items-center space-x-2",
      span(cls := "text-xs font-semibold text-gray-500 dark:text-gray-400", "並び替え:"),
      select(
        cls := "text-xs px-2 py-1 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 outline-none",
        value <-- sortType.signal.map {
          case SortByDateDesc     => "date-desc"
          case SortByDateAsc      => "date-asc"
          case SortByCommentCount => "comment-count"
          case _                  => "date-desc"
        },
        onChange.mapToValue.map {
          case "date-desc"     => SortByDateDesc
          case "date-asc"      => SortByDateAsc
          case "comment-count" => SortByCommentCount
          case _               => SortByDateDesc
        } --> sortType,
        option(value := "date-desc", "投稿日時（新しい順）"),
        option(value := "date-asc", "投稿日時（古い順）"),
        option(value := "comment-count", "コメント数")
      )
    )

  private def jokeToggle(showJokes: Var[Boolean]): HtmlElement =
    label(
      cls := "flex items-center cursor-pointer",
      span(cls := "mr-2 text-sm font-semibold text-gray-700 dark:text-gray-300", "ジョークを含む"),
      div(
        cls := "relative",
        input(
          typ := "checkbox",
          cls := "sr-only",
          checked <-- showJokes.signal,
          onInput.mapToChecked --> showJokes
        ),
        div(
          cls := "w-10 h-5 rounded-full shadow-inner transition-colors",
          cls <-- showJokes.signal.map(if (_) "bg-orange-500" else "bg-gray-200 dark:bg-gray-700")
        ),
        div(
          cls := "absolute w-4 h-4 bg-neutral-50 dark:bg-neutral-50 rounded-full shadow-md top-0.5 left-0.5 transition-transform",
          cls <-- showJokes.signal.map(if (_) "translate-x-5" else "translate-x-0")
        )
      )
    )

  private def threadFilter(
      questionFilter: Var[QuestionFilter],
      threadTypeFilter: Var[ThreadTypeFilter]
  ): HtmlElement =
    div(
      cls := "flex items-center space-x-2",
      span(cls := "text-xs font-semibold text-gray-500 dark:text-gray-400", "表示:"),
      select(
        cls := "text-xs px-2 py-1 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 text-gray-800 dark:text-gray-200 focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 outline-none",
        value <-- Signal.combine(questionFilter.signal, threadTypeFilter.signal).map {
          case (ShowAll, AllTypes)        => "all"
          case (ShowResolved, AllTypes)   => "resolved"
          case (ShowUnresolved, AllTypes) => "unresolved"
          case (ShowAll, TypeQuestion)    => "question"
          case (ShowAll, TypeDiscussion)  => "discussion"
          case (ShowAll, TypeChat)        => "chat"
          case (ShowAll, TypeShare)       => "share"
          case _                          => "all"
        },
        onChange.mapToValue.map {
          case "all"        => (ShowAll, AllTypes)
          case "resolved"   => (ShowResolved, AllTypes)
          case "unresolved" => (ShowUnresolved, AllTypes)
          case "question"   => (ShowAll, TypeQuestion)
          case "discussion" => (ShowAll, TypeDiscussion)
          case "chat"       => (ShowAll, TypeChat)
          case "share"      => (ShowAll, TypeShare)
          case _            => (ShowAll, AllTypes)
        } --> { case (nextQuestionFilter, nextThreadTypeFilter) =>
          questionFilter.set(nextQuestionFilter)
          threadTypeFilter.set(nextThreadTypeFilter)
        },
        option(value := "all", "すべて"),
        option(value := "resolved", "解決済み"),
        option(value := "unresolved", "未解決"),
        option(value := "question", "質問のみ"),
        option(value := "discussion", "議論のみ"),
        option(value := "chat", "雑談のみ"),
        option(value := "share", "共有のみ")
      )
    )
}
