package general.mypage

import general.thread.CommentItem

import com.raquo.laminar.api.L.*
import general.navigation.Router
import general.thread.ThreadItem
import models.*
import org.scalajs.dom

object MyPostsLists {
  def apply(state: MyPostsState, actions: MyPostsActions): Signal[HtmlElement] =
    state.isFetchingVar.signal.flatMapSwitch { loading =>
      if (loading) Val(loadingView())
      else
        state.activeTabVar.signal.flatMapSwitch {
          case MyPostsTab.Words    => state.wordsVar.signal.map(items => list(items, wordCard))
          case MyPostsTab.Meanings =>
            state.meaningsVar.signal.map(items =>
              list(
                items,
                item =>
                  MyPageWordItem
                    .meaningCard(item, onDelete = Some(actions.deleteMeaning), onEdit = Some(actions.updateMeaning))
              )
            )
          case MyPostsTab.Examples =>
            state.examplesVar.signal.map(items =>
              list(
                items,
                item =>
                  MyPageWordItem
                    .exampleCard(item, onDelete = Some(actions.deleteExample), onEdit = Some(actions.updateExample))
              )
            )
          case MyPostsTab.Threads  => state.threadsVar.signal.map(items => list(items, threadCard(_, actions)))
          case MyPostsTab.Comments => state.commentsVar.signal.map(items => list(items, commentCard(_, actions)))
        }
    }

  private def wordCard(item: MyPageWordTagViewValue): HtmlElement =
    div(
      cls := "bg-white dark:bg-gray-900 p-4 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 space-y-3",
      div(
        cls := "flex items-start justify-between gap-3",
        div(
          cls := "min-w-0",
          div(
            cls := "flex items-center gap-2",
            span(cls := "text-sm font-bold text-blue-600 dark:text-blue-400 truncate", item.wordMain),
            span(cls := "text-[10px] text-gray-300 dark:text-gray-600", "言葉")
          ),
          div(
            cls := "mt-1 flex flex-wrap items-center gap-2 text-[11px]",
            span(
              cls := "rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 font-bold text-gray-500 dark:text-gray-400",
              s"#${item.tagName}"
            ),
            span(cls := "text-gray-400 dark:text-gray-500", item.wordReading)
          )
        ),
        button(
          cls := "shrink-0 text-[11px] font-bold text-blue-600 dark:text-blue-400 active:opacity-50",
          onClick --> (_ => Router.router.pushState(WordDetailPage(item.wordId, item.tagId))),
          "詳細を見る"
        )
      ),
      p(cls := "text-xs text-gray-500 dark:text-gray-400 leading-relaxed", "語釈や用例を付けずに登録した言葉も、ここから確認できます。")
    )

  private def threadCard(thread: ThreadViewValue, actions: MyPostsActions): HtmlElement =
    div(
      cls := "relative",
      ThreadItem
        .threadItem(
          thread,
          isMyPost = true,
          onDelete = Some(actions.deleteThread),
          onEdit = Some(actions.updateThread),
          clickableCard = false
        )
        .amend(
          onClick --> { event =>
            val target = event.target.asInstanceOf[dom.html.Element]
            if (target.tagName == "BUTTON" && target.innerText.contains("スレッドを見る")) {
              event.stopPropagation()
              Router.router.pushState(ThreadDetailPage(thread.threadId))
            }
          }
        )
    )

  private def commentCard(comment: CommentViewValue, actions: MyPostsActions): HtmlElement = {
    val commentWithParent = comment.copy(comment = comment.comment.copy(parentCommentId = Some(-1L)))
    div(
      cls := "relative",
      CommentItem(
        cv = commentWithParent,
        depth = 0,
        onReactionToggle = (_, _) => (),
        isReadOnly = false,
        isOwnComment = true,
        onEdit = Some(text => actions.updateComment(comment.comment.commentId.get, text)),
        onDelete = Some(() => actions.deleteComment(comment.comment.commentId.get))
      ).amend(
        cls := "cursor-pointer hover:shadow-md transition-shadow",
        onClick --> { event =>
          val target = event.target.asInstanceOf[dom.html.Element]
          if (target.closest("button") == null && target.tagName != "BUTTON") {
            Router.router.pushState(ThreadDetailPage(comment.comment.threadId))
          }
        }
      )
    )
  }

  private def list[A](items: Seq[A], renderCard: A => HtmlElement): HtmlElement =
    if (items.isEmpty) emptyView()
    else div(cls := "space-y-4", items.map(renderCard))

  private def loadingView(): HtmlElement =
    div(
      cls := "py-20 text-center",
      div(cls := "inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent")
    )

  private def emptyView(): HtmlElement =
    div(
      cls := "py-20 text-center space-y-2",
      svg.svg(
        svg.cls := "w-16 h-16 mx-auto text-gray-400",
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 24 24",
        svg.path(
          svg.d := "M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"
        )
      ),
      p(cls := "text-gray-400 text-sm", "投稿がありません")
    )
}
