package general.quote

import com.raquo.laminar.api.L.*
import general.word.WordDetailApi
import shared.api.{ContentQuoteTypes, ContentQuoteViewValue}
import scala.concurrent.ExecutionContext.Implicits.global

object ContentQuotePicker {
  def apply(wordId: Long, tagId: Long, onSelect: ContentQuoteViewValue => Unit): HtmlElement = {
    val choices = Var(Seq.empty[ContentQuoteViewValue])
    val loading = Var(true)

    div(
      cls := "rounded-xl border border-gray-200 bg-white p-3 shadow-lg dark:border-gray-700 dark:bg-gray-900",
      p(cls := "mb-2 text-xs font-bold text-gray-600 dark:text-gray-300", "引用する語釈・用例を選択"),
      child <-- loading.signal.map {
        case true                           => div(cls := "py-4 text-center text-xs text-gray-400", "読み込み中...")
        case false if choices.now().isEmpty => div(cls := "py-4 text-center text-xs text-gray-400", "引用できる投稿がありません")
        case false                          =>
          div(
            cls := "max-h-64 space-y-2 overflow-y-auto",
            choices
              .now()
              .map(choice =>
                button(
                  tpe := "button",
                  cls := "block w-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500/50",
                  ContentQuoteCard(choice, navigate = false),
                  onClick.stopPropagation --> (_ => onSelect(choice))
                )
              )
          )
      },
      onMountCallback { _ =>
        for {
          meanings <- WordDetailApi.fetchAllMeanings(wordId, tagId)
          examples <- WordDetailApi.fetchAllExamples(wordId, tagId)
        } yield {
          val meaningChoices = meanings.flatMap { item =>
            item.meaning.meaningId.map(id =>
              ContentQuoteViewValue(
                ContentQuoteTypes.Meaning,
                id,
                false,
                Some(item.meaning.meaningText),
                Some(item.userName),
                item.displayId,
                Some(wordId),
                Some(tagId),
                None,
                None
              )
            )
          }
          val exampleChoices = examples.flatMap { item =>
            item.example.exampleId.map(id =>
              ContentQuoteViewValue(
                ContentQuoteTypes.Example,
                id,
                false,
                Some(item.example.exampleText),
                Some(item.userName),
                item.displayId,
                Some(wordId),
                Some(tagId),
                None,
                None
              )
            )
          }
          choices.set(meaningChoices ++ exampleChoices)
          loading.set(false)
        }
      }
    )
  }
}
