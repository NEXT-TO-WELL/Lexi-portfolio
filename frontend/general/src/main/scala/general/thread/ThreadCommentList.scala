package general.thread

import general.thread.CommentItem
import general.auth.AuthStorage

import com.raquo.laminar.api.L.*
import component.*
import models.{CommentViewValue, ThreadViewValue}
import org.scalajs.dom
import org.scalajs.dom.IntersectionObserver

object ThreadCommentList {
  final case class Actions(
      openReply: (CommentViewValue, Option[CommentViewValue]) => Unit,
      toggleReaction: (Long, String) => Unit,
      report: Long => Unit,
      edit: (Long, String) => Unit,
      delete: Long => Unit,
      selectBestAnswer: (ThreadViewValue, CommentViewValue) => Unit,
      loadMore: () => Unit
  )

  def apply(
      comments: Signal[Seq[CommentViewValue]],
      thread: Signal[Option[ThreadViewValue]],
      loading: Signal[Boolean],
      hasMore: Signal[Boolean],
      isLoading: () => Boolean,
      actions: Actions
  ): Signal[HtmlElement] =
    comments.combineWith(thread).map { case (items, currentThread) =>
      val roots = items.filter(_.comment.parentCommentId.isEmpty).sortWith { (left, right) =>
        if (left.isBestAnswer != right.isBestAnswer) left.isBestAnswer
        else left.comment.commentDate < right.comment.commentDate
      }

      div(
        cls := "p-4 pb-48 space-y-4",
        h3(
          cls := "text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase tracking-widest ml-1",
          s"${items.size} Comments"
        ),
        if (roots.isEmpty) {
          div(cls := "py-20 text-center text-gray-400 dark:text-gray-500 text-sm", "まだコメントはありません")
        } else {
          div(cls := "space-y-4", roots.map(renderRootComment(_, items, currentThread, actions)))
        },
        paginationTrigger(loading, hasMore, isLoading, actions.loadMore)
      )
    }

  private def renderRootComment(
      comment: CommentViewValue,
      allComments: Seq[CommentViewValue],
      thread: Option[ThreadViewValue],
      actions: Actions
  ): HtmlElement = {
    val rootId = comment.comment.commentId.getOrElse(0L)
    val bestDescendant = if (comment.isBestAnswer) None else findBestDescendant(rootId, allComments)
    val replyCount = countReplies(rootId, allComments)
    val isGuest = AuthStorage.getToken.isEmpty
    val currentUserId = AuthStorage.getUserId
    val isOwnComment = currentUserId.exists(comment.comment.userId.contains)
    val canSelectBest = thread.exists { current =>
      current.userId.contains(currentUserId.getOrElse("")) &&
      current.questionStatus.exists(!_.isResolved) &&
      !comment.isBestAnswer
    }

    div(
      idAttr := s"comment-$rootId",
      cls := "bg-white dark:bg-gray-900 p-4 rounded-xl border border-gray-100 dark:border-gray-800 shadow-sm",
      CommentItem(
        cv = comment,
        depth = 0,
        onReplyClick = Some(() => {
          if (isGuest) {
            Modal.show("ログインが必要です", "返信するにはログインしてください。", ModalType.Info)
          } else {
            dom.window.location.hash = "reply"
            actions.openReply(comment, Some(comment))
          }
        }),
        onReactionToggle = actions.toggleReaction,
        isReadOnly = isGuest,
        onReport = if (isGuest) None else Some(actions.report),
        isOwnComment = isOwnComment,
        onEdit = if (isOwnComment) Some(text => actions.edit(rootId, text)) else None,
        onDelete = if (isOwnComment) Some(() => actions.delete(rootId)) else None,
        showBestAnswerTrigger = canSelectBest,
        onSelectBestAnswer = thread.map(current => () => actions.selectBestAnswer(current, comment))
      ),
      if (replyCount > 0) {
        button(
          cls := "mt-3 w-full text-xs font-bold text-blue-600 dark:text-blue-300 flex items-center justify-center space-x-1 p-2 bg-blue-50/50 dark:bg-blue-900/20 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition",
          span(s"▼ 返信 $replyCount 件を表示"),
          onClick --> { _ =>
            dom.window.location.hash = "reply"
            actions.openReply(comment, None)
          }
        )
      } else emptyNode,
      bestDescendant.map(bestAnswerPreview(comment, _, actions.openReply)).getOrElse(emptyNode)
    )
  }

  private def findBestDescendant(
      parentId: Long,
      comments: Seq[CommentViewValue]
  ): Option[CommentViewValue] = {
    val children = comments.filter(_.comment.parentCommentId.contains(parentId))
    children
      .find(_.isBestAnswer)
      .orElse(
        children.flatMap(child => child.comment.commentId.flatMap(findBestDescendant(_, comments))).headOption
      )
  }

  private def countReplies(parentId: Long, comments: Seq[CommentViewValue]): Int = {
    val children = comments.filter(_.comment.parentCommentId.contains(parentId))
    children.size + children.flatMap(_.comment.commentId.map(countReplies(_, comments))).sum
  }

  private def bestAnswerPreview(
      root: CommentViewValue,
      bestAnswer: CommentViewValue,
      openReply: (CommentViewValue, Option[CommentViewValue]) => Unit
  ): HtmlElement =
    div(
      cls := "mt-2 p-3 bg-amber-50 dark:bg-amber-900/20 rounded-lg border border-amber-100 dark:border-amber-800 cursor-pointer hover:bg-amber-100 dark:hover:bg-amber-900/30 transition",
      onClick --> { _ =>
        dom.window.location.hash = s"reply-comment-${bestAnswer.comment.commentId.getOrElse(0L)}"
        openReply(root, None)
      },
      div(
        cls := "flex items-center space-x-1 mb-1",
        svg.svg(
          svg.cls := "w-3.5 h-3.5 fill-amber-600 dark:fill-amber-300",
          svg.viewBox := "0 0 24 24",
          svg.path(
            svg.d := "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"
          )
        ),
        span(cls := "text-[10px] font-bold text-amber-700 dark:text-amber-400", "この返信欄にベストアンサーがあります")
      ),
      div(
        cls := "text-[11px] text-gray-600 dark:text-gray-300 line-clamp-1 italic",
        s"「${bestAnswer.comment.commentText}」"
      ),
      div(cls := "mt-1 text-[9px] text-amber-600 dark:text-amber-400 font-bold text-right", "タップしてベストアンサーへ移動 →")
    )

  private def paginationTrigger(
      loading: Signal[Boolean],
      hasMore: Signal[Boolean],
      isLoading: () => Boolean,
      loadMore: () => Unit
  ): HtmlElement =
    div(
      child <-- hasMore.map {
        case false => emptyNode
        case true  => infiniteScrollTrigger(loading, isLoading, loadMore)
      }
    )

  private def infiniteScrollTrigger(
      loading: Signal[Boolean],
      isLoading: () => Boolean,
      loadMore: () => Unit
  ): HtmlElement = {
    var observer = Option.empty[IntersectionObserver]

    div(
      cls := "py-4 text-center text-gray-400 dark:text-gray-500 text-sm",
      child <-- loading.map(if (_) span("読み込み中...") else span("さらに読み込む")),
      onMountCallback { context =>
        val nextObserver = new IntersectionObserver((entries, _) => {
          entries.foreach { entry =>
            if (entry.isIntersecting && !isLoading()) loadMore()
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
}
