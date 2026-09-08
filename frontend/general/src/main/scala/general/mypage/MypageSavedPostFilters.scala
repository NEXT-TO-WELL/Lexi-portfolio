package general.mypage

import com.raquo.laminar.api.L.*
import models.{MyPageExampleViewValue, MyPageMeaningViewValue}

enum ReactionFilter {
  case All, Good, Helpful, Funny
}

object MypageSavedPostFilters {
  def searchBar(query: Var[String], placeholderText: String): HtmlElement =
    div(
      cls := "relative",
      input(
        tpe := "text",
        cls := "w-full px-4 py-2 pl-10 pr-10 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent",
        placeholder := placeholderText,
        value <-- query.signal,
        onInput.mapToValue --> query
      ),
      svg.svg(
        svg.cls := "absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none",
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 24 24",
        svg.path(svg.d := "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z")
      ),
      child <-- query.signal.map { value =>
        if (value.nonEmpty) {
          button(
            cls := "absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600 transition",
            onClick --> (_ => query.set("")),
            "×"
          )
        } else emptyNode
      }
    )

  def filterMeanings(
      items: Seq[MyPageMeaningViewValue],
      query: String,
      reaction: Option[ReactionFilter] = None
  ): Seq[MyPageMeaningViewValue] =
    filterBySearch(
      items.filter(item =>
        reaction.forall(
          matchesReaction(item.meaningView.isGood, item.meaningView.isHelpful, item.meaningView.isFunny, _)
        )
      ),
      query
    )(item => Seq(item.meaningView.meaning.meaningText, item.wordMain, item.tagName))

  def filterExamples(
      items: Seq[MyPageExampleViewValue],
      query: String,
      reaction: Option[ReactionFilter] = None
  ): Seq[MyPageExampleViewValue] =
    filterBySearch(
      items.filter(item =>
        reaction.forall(
          matchesReaction(item.exampleView.isGood, item.exampleView.isHelpful, item.exampleView.isFunny, _)
        )
      ),
      query
    )(item => Seq(item.exampleView.example.exampleText, item.wordMain, item.tagName))

  def list[A](items: Seq[A], query: String, emptyMessage: String)(renderCard: A => HtmlElement): HtmlElement =
    if (items.isEmpty) {
      val message = if (query.trim.nonEmpty) "検索結果が見つかりませんでした" else emptyMessage
      div(
        cls := "py-20 text-center text-gray-400 dark:text-gray-500 space-y-2",
        svg.svg(
          svg.cls := "w-16 h-16 mx-auto",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "1.5",
          svg.viewBox := "0 0 24 24",
          svg.path(svg.d := "M14 9V5a3 3 0 00-6 0v4m-3 0h12l-1 10H6L5 9zm4 4h.01M15 13h.01")
        ),
        p(message)
      )
    } else div(cls := "space-y-4", items.map(renderCard))

  def loading(): HtmlElement =
    div(
      cls := "py-20 text-center",
      div(cls := "inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent")
    )

  private def matchesReaction(good: Boolean, helpful: Boolean, funny: Boolean, filter: ReactionFilter): Boolean =
    filter match {
      case ReactionFilter.All     => good || helpful || funny
      case ReactionFilter.Good    => good
      case ReactionFilter.Helpful => helpful
      case ReactionFilter.Funny   => funny
    }

  private def filterBySearch[A](items: Seq[A], query: String)(texts: A => Seq[String]): Seq[A] = {
    val normalized = query.toLowerCase.trim
    if (normalized.isEmpty) items else items.filter(item => texts(item).exists(_.toLowerCase.contains(normalized)))
  }
}
