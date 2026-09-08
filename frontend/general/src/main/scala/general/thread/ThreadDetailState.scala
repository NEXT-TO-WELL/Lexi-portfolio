package general.thread

import com.raquo.laminar.api.L.Var
import models.{CommentViewValue, ThreadViewValue}

final class ThreadDetailState {
  val threadVar = Var(Option.empty[ThreadViewValue])
  val commentsVar = Var(Seq.empty[CommentViewValue])
  val activeReplyParentVar = Var(Option.empty[CommentViewValue])
  val replyToVar = Var(Option.empty[CommentViewValue])
  val commentsLoadingVar = Var(false)
  val commentsHasMoreVar = Var(true)

  private var currentThreadId = Option.empty[Long]
  private var loadedThreadId = Option.empty[Long]

  def prepare(threadId: Long): Unit =
    if (!currentThreadId.contains(threadId)) {
      currentThreadId = Some(threadId)
      loadedThreadId = None
      threadVar.set(None)
      commentsVar.set(Nil)
      clearReply()
      commentsLoadingVar.set(false)
      commentsHasMoreVar.set(true)
    }

  def needsInitialLoad(threadId: Long): Boolean =
    !loadedThreadId.contains(threadId)

  def markLoaded(threadId: Long): Unit =
    loadedThreadId = Some(threadId)

  def clearReply(): Unit = {
    activeReplyParentVar.set(None)
    replyToVar.set(None)
  }
}
