package general.thread

import com.raquo.laminar.api.L.*
import general.auth.AuthStorage
import general.thread.CommentItem
import models.CommentViewValue

object ThreadReplyDetail {
  final case class Actions(
      reply: CommentViewValue => Unit,
      toggleReaction: (Long, String) => Unit,
      report: Option[Long => Unit] = None,
      selectBestAnswer: Option[Long => Unit] = None,
      edit: Option[(Long, String) => Unit] = None,
      delete: Option[Long => Unit] = None
  )

  def apply(
      parent: CommentViewValue,
      comments: Signal[Seq[CommentViewValue]],
      isThreadOwner: Boolean,
      isUnresolved: Signal[Boolean],
      actions: Actions
  ): HtmlElement = {
    val isGuest = AuthStorage.getToken.isEmpty

    div(
      cls := "w-full min-h-full bg-gray-50 dark:bg-gray-950 pb-48 animate-fadeIn",
      parentComment(parent, comments, isThreadOwner, isUnresolved, isGuest, actions),
      replyList(parent, comments, isThreadOwner, isUnresolved, isGuest, actions)
    )
  }

  private def parentComment(
      parent: CommentViewValue,
      comments: Signal[Seq[CommentViewValue]],
      isThreadOwner: Boolean,
      isUnresolved: Signal[Boolean],
      isGuest: Boolean,
      actions: Actions
  ): HtmlElement =
    div(
      cls := "p-4 bg-white dark:bg-gray-900 sticky top-0 z-10 backdrop-blur-sm border-b border-gray-100 dark:border-gray-800",
      div(
        cls := "mb-2",
        span(cls := "text-[10px] text-blue-600 dark:text-blue-400 font-bold", "返信元のコメント")
      ),
      child <-- Signal
        .combine(
          comments.map(_.find(_.comment.commentId == parent.comment.commentId).getOrElse(parent)),
          isUnresolved
        )
        .map { case (latest, unresolved) =>
          renderComment(latest, depth = 0, None, isThreadOwner, unresolved, isGuest, actions)
        }
    )

  private def replyList(
      parent: CommentViewValue,
      comments: Signal[Seq[CommentViewValue]],
      isThreadOwner: Boolean,
      isUnresolved: Signal[Boolean],
      isGuest: Boolean,
      actions: Actions
  ): HtmlElement =
    div(
      child <-- Signal.combine(comments, isUnresolved).map { case (allComments, unresolved) =>
        val childrenByParent = allComments.groupBy(_.comment.parentCommentId)

        def flatten(parentId: Long, depth: Int, parentName: String): Seq[HtmlElement] =
          childrenByParent.getOrElse(Some(parentId), Seq.empty).flatMap { child =>
            val commentId = child.comment.commentId.getOrElse(-1L)
            val item = div(
              idAttr := s"comment-$commentId",
              cls := "p-4",
              renderComment(
                child,
                depth = depth.min(2),
                replyToName = Option.when(depth >= 1)(parentName),
                isThreadOwner,
                unresolved,
                isGuest,
                actions
              )
            )
            item +: flatten(commentId, depth + 1, child.userName)
          }

        val replies = flatten(parent.comment.commentId.getOrElse(-1L), 1, parent.userName)
        div(
          cls := "bg-gray-50 dark:bg-gray-950",
          if (replies.isEmpty) {
            div(cls := "py-16 text-center text-sm text-gray-400 dark:text-gray-500", "まだ返信はありません")
          } else replies
        )
      }
    )

  private def renderComment(
      comment: CommentViewValue,
      depth: Int,
      replyToName: Option[String],
      isThreadOwner: Boolean,
      isUnresolved: Boolean,
      isGuest: Boolean,
      actions: Actions
  ): HtmlElement = {
    val commentId = comment.comment.commentId.getOrElse(-1L)
    val isOwnComment = AuthStorage.getUserId.exists(comment.comment.userId.contains)
    val canSelectBest = isThreadOwner && isUnresolved && !comment.isBestAnswer

    CommentItem(
      cv = comment,
      depth = depth,
      replyToName = replyToName,
      onReplyClick = Some(() => actions.reply(comment)),
      onReactionToggle = actions.toggleReaction,
      isReadOnly = isGuest,
      onReport = actions.report,
      isOwnComment = isOwnComment,
      onEdit = if (isOwnComment) actions.edit.map(edit => text => edit(commentId, text)) else None,
      onDelete = if (isOwnComment) actions.delete.map(delete => () => delete(commentId)) else None,
      showBestAnswerTrigger = canSelectBest,
      onSelectBestAnswer = Option.when(canSelectBest) { () =>
        actions.selectBestAnswer.foreach(_(commentId))
      }
    )
  }
}
