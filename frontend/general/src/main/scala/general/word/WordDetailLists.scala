package general.word

import com.raquo.laminar.api.L.*
import general.word.DetailPostForm
import general.thread.ThreadItem
import general.word.WordItem
import models.ThreadViewValue
import org.scalajs.dom.IntersectionObserver
import shared.api.{ExampleViewValue, MeaningViewValue}
import shared.api.ContentQuoteViewValue
import general.quote.ContentQuoteCard

object WordDetailLists {
  final case class Pagination(
      hasMore: Signal[Boolean],
      loading: Signal[Boolean],
      isLoading: () => Boolean,
      loadMore: () => Unit
  )

  final case class MeaningActions(
      showPostForm: () => Unit,
      favoriteChanged: (Long, Boolean) => Unit,
      delete: Long => Unit,
      edit: (Long, String, Boolean) => Unit,
      reactionChanged: (Long, String, Boolean, Int) => Unit,
      quote: MeaningViewValue => Unit
  )

  final case class ExampleActions(
      showPostForm: () => Unit,
      favoriteChanged: (Long, Boolean) => Unit,
      delete: Long => Unit,
      edit: (Long, String, Boolean) => Unit,
      reactionChanged: (Long, String, Boolean, Int) => Unit,
      quote: ExampleViewValue => Unit
  )

  final case class ThreadActions(
      showPostForm: () => Unit,
      closePostForm: () => Unit,
      post: (String, String, Option[ContentQuoteViewValue], () => Unit) => Unit,
      clearQuote: () => Unit
  )

  def meanings(
      items: Seq[MeaningViewValue],
      currentUserId: Option[String],
      isGuest: Boolean,
      actions: MeaningActions,
      pagination: Pagination
  ): HtmlElement =
    div(
      cls := "pt-4",
      postAction(
        isGuest,
        "語釈を投稿するにはログインが必要です",
        "+ 語釈を投稿する",
        actions.showPostForm
      ),
      renderList(
        items,
        item =>
          WordItem.meaningCard(
            item,
            isMy = currentUserId.contains(item.meaning.userId.getOrElse("")),
            onFav = Some(isFavorite => item.meaning.meaningId.foreach(actions.favoriteChanged(_, isFavorite))),
            onDelete = Some(actions.delete),
            onEdit = Some(actions.edit),
            onReactionChange = Some(actions.reactionChanged),
            onQuote = Some(() => actions.quote(item))
          )
      ),
      paginationTrigger(pagination)
    )

  def examples(
      items: Seq[ExampleViewValue],
      currentUserId: Option[String],
      isGuest: Boolean,
      actions: ExampleActions,
      pagination: Pagination
  ): HtmlElement =
    div(
      cls := "pt-4",
      postAction(
        isGuest,
        "用例を投稿するにはログインが必要です",
        "+ 用例を投稿する",
        actions.showPostForm
      ),
      renderList(
        items,
        item =>
          WordItem.exampleCard(
            item,
            isMy = currentUserId.contains(item.example.userId.getOrElse("")),
            onFav = Some(isFavorite => item.example.exampleId.foreach(actions.favoriteChanged(_, isFavorite))),
            onDelete = Some(actions.delete),
            onEdit = Some(actions.edit),
            onReactionChange = Some(actions.reactionChanged),
            onQuote = Some(() => actions.quote(item))
          )
      ),
      paginationTrigger(pagination)
    )

  def threads(
      wordId: Long,
      tagId: Long,
      items: Signal[Seq[ThreadViewValue]],
      isGuest: Boolean,
      postFormOpen: Signal[Boolean],
      selectedQuote: Var[Option[ContentQuoteViewValue]],
      actions: ThreadActions,
      pagination: Pagination
  ): HtmlElement =
    div(
      cls := "pt-4",
      child <-- postFormOpen.map {
        case false =>
          postAction(
            isGuest,
            "スレッドを立てるにはログインが必要です",
            "+ 新しいスレッドを立てる",
            actions.showPostForm
          )
        case true =>
          div(
            cls := "p-4 bg-gray-50 dark:bg-gray-900 rounded-2xl mb-4 border border-gray-200 dark:border-gray-700",
            child <-- selectedQuote.signal.map {
              case Some(quote) => div(cls := "mb-3", ContentQuoteCard(quote, Some(actions.clearQuote)))
              case None        => emptyNode
            },
            DetailPostForm(
              postType = DetailPostForm.Thread,
              draftContext = "word-$wordId-tag-$tagId",
              onPost = (payload, onSuccess) =>
                actions.post(payload.text, payload.typeId.getOrElse("3"), selectedQuote.now(), onSuccess),
              onCancel = actions.closePostForm
            )
          )
      },
      child <-- items.map(current =>
        renderList(current, thread => ThreadItem.threadItem(thread, clickableCard = true))
      ),
      paginationTrigger(pagination)
    )

  private def postAction(
      isGuest: Boolean,
      guestMessage: String,
      buttonLabel: String,
      onClickAction: () => Unit
  ): HtmlElement =
    if (isGuest) {
      div(
        cls := "w-full py-3 mb-4 bg-gray-100 dark:bg-gray-900 text-gray-500 dark:text-gray-400 text-center font-bold rounded-xl border-2 border-dashed border-gray-200 dark:border-gray-700",
        guestMessage
      )
    } else {
      button(
        cls := "w-full py-3 mb-4 bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-300 font-bold rounded-xl border-2 border-dashed border-blue-200 dark:border-blue-800 hover:bg-blue-100 dark:hover:bg-blue-900/30 transition",
        buttonLabel,
        onClick --> (_ => onClickAction())
      )
    }

  private def paginationTrigger(pagination: Pagination): HtmlElement =
    div(
      child <-- pagination.hasMore.map {
        case false => emptyNode
        case true  => infiniteScrollTrigger(pagination)
      }
    )

  private def infiniteScrollTrigger(pagination: Pagination): HtmlElement = {
    var observer = Option.empty[IntersectionObserver]

    div(
      cls := "py-4 text-center text-gray-400",
      child <-- pagination.loading.map {
        case true  => span("読み込み中...")
        case false => span("さらに読み込む")
      },
      onMountCallback { context =>
        val nextObserver = new IntersectionObserver((entries, _) => {
          entries.foreach { entry =>
            if (entry.isIntersecting && !pagination.isLoading()) {
              pagination.loadMore()
            }
          }
        })
        nextObserver.observe(context.thisNode.ref)
        observer = Some(nextObserver)
      },
      onUnmountCallback { _ =>
        observer.foreach(_.disconnect())
        observer = None
      }
    )
  }

  private def renderList[A](items: Seq[A], renderCard: A => HtmlElement): HtmlElement =
    if (items.isEmpty) div(cls := "text-center text-gray-400 py-20", "まだ投稿がありません")
    else div(cls := "space-y-4 pt-4", items.map(renderCard))
}
