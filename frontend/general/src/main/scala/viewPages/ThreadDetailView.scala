package viewPages

import general.auth.AuthStorage
import general.ui.PullToRefresh

import com.raquo.laminar.api.L.*
import general.thread.{
  ThreadCommentInput,
  ThreadCommentList,
  ThreadDetailActions,
  ThreadDetailHeader,
  ThreadDetailState,
  ThreadReplyDetail
}
import models.*
import org.scalajs.dom

object ThreadDetailView {
  private val state = new ThreadDetailState
  private val actions = new ThreadDetailActions(state)
  import state.*

  def apply(page: ThreadDetailPage): HtmlElement = {
    state.prepare(page.threadId)

    div(
      // 共通シェル内の利用可能領域に収め、固定入力欄の基準にもする。
      cls := "relative w-full h-full min-h-0 flex flex-col bg-gray-50 dark:bg-gray-950 overflow-hidden",

      onMountCallback { ctx =>
        windowEvents(_.onPopState).foreach { _ =>
          if (!dom.window.location.hash.contains("reply") && activeReplyParentVar.now().isDefined) {
            activeReplyParentVar.set(None)
            replyToVar.set(None)
          }
        }(ctx.owner) // ★ 所有権を ctx.owner に紐付けることで自動クリーンアップ
        if (state.needsInitialLoad(page.threadId)) {
          actions.fetchThreadDetail(page.threadId)
        }
      },
      onUnmountCallback(_ => state.clearReply()),
      // 2. 透明なスペーサー（MobileのHeader pt-14 分）
      // アプリ共通ヘッダーの下から開始させるため
      div(cls := "flex-none h-14"),

      // 3. 固定スレッドヘッダー（flex-none で高さを固定）
      div(
        cls := "flex-none bg-white/95 dark:bg-gray-900/95 backdrop-blur-md border-b border-gray-100 dark:border-gray-800 shadow-sm z-30",
        child <-- threadVar.signal.map {
          case None         => div(cls := "p-4 text-center text-gray-400 dark:text-gray-500 text-sm", "読み込み中...")
          case Some(thread) =>
            ThreadDetailHeader(
              thread,
              ThreadDetailHeader.Actions(
                updateTitle = title => actions.updateThreadTitle(thread.threadId, title),
                delete = () => actions.deleteThread(thread),
                report = actions.reportThread
              )
            )
        }
      ),

      // 4. メインコンテンツ（flex-grow と overflow-y-auto でここだけスクロール）
      PullToRefresh(() => actions.fetchThreadDetail(page.threadId, force = true))(
        child <-- activeReplyParentVar.signal.flatMapSwitch {
          case None =>
            replyToVar.set(None)
            ThreadCommentList(
              comments = commentsVar.signal,
              thread = threadVar.signal,
              loading = commentsLoadingVar.signal,
              hasMore = commentsHasMoreVar.signal,
              isLoading = () => commentsLoadingVar.now(),
              actions = ThreadCommentList.Actions(
                openReply = (parent, replyTo) => {
                  activeReplyParentVar.set(Some(parent))
                  replyToVar.set(replyTo)
                },
                toggleReaction = actions.toggleReaction,
                report = actions.reportComment,
                edit = actions.updateComment,
                delete = actions.deleteComment,
                selectBestAnswer = (thread, comment) => {
                  comment.comment.commentId.foreach(
                    actions.confirmBestAnswer(
                      thread.threadId,
                      _,
                      s"${comment.userName} さんの回答をベストアンサーに選んで、解決済みにしますか？\n※この操作は取り消せません。"
                    )
                  )
                },
                loadMore = () => threadVar.now().foreach(thread => actions.fetchMoreComments(thread.threadId))
              )
            )
          case Some(parent) =>
            val currentUserId = AuthStorage.getUserId.getOrElse("")
            val threadOpt = threadVar.now()
            val isThreadOwner = threadOpt.exists(_.userId.contains(currentUserId))
            val isUnresolvedSignal = threadVar.signal.map(_.exists(_.questionStatus.exists(!_.isResolved)))
            Signal.fromValue(
              ThreadReplyDetail(
                parent = parent,
                comments = commentsVar.signal,
                isThreadOwner = isThreadOwner,
                isUnresolved = isUnresolvedSignal,
                actions = ThreadReplyDetail.Actions(
                  reply = comment => replyToVar.set(Some(comment)),
                  toggleReaction = actions.toggleReaction,
                  report = Some(actions.reportComment),
                  selectBestAnswer = Some(commentId => {
                    threadOpt.foreach(thread =>
                      actions.confirmBestAnswer(
                        thread.threadId,
                        commentId,
                        "この回答をベストアンサーに選んで、解決済みにしますか？\n※この操作は取り消せません。"
                      )
                    )
                  }),
                  edit = Some(actions.updateComment),
                  delete = Some(actions.deleteComment)
                )
              )
            )
        }
      ),

      // 5. 固定入力フォーム
      ThreadCommentInput(
        threadId = page.threadId,
        thread = threadVar.signal,
        activeReplyParent = activeReplyParentVar,
        replyTo = replyToVar,
        onCommentPosted = actions.commentPosted
      )
    )
  }

}
