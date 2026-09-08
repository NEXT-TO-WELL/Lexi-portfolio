package general.thread

import general.auth.AuthStorage
import general.ui.EditModal
import general.ui.ReportModal

import component.*
import general.navigation.{Router, TabStateManager}
import models.*
import shared.api.ReactionToggleRequest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

final class ThreadDetailActions(state: ThreadDetailState) {
  import state.*

  def toggleReaction(commentId: Long, reactionType: String): Unit = {
    if (AuthStorage.getToken.isEmpty) {
      Modal.show("ログインが必要です", "リアクション機能を利用するにはログインしてください。", ModalType.Info)
      return
    }

    val request = ReactionToggleRequest("4", commentId, reactionType)
    util.ErrorHandler.handle(
      ThreadDetailApi.toggleReaction(request),
      onSuccess = { response =>
        commentsVar.update(_.map { comment =>
          if (comment.comment.commentId.contains(commentId)) {
            val isGoodClick = reactionType == "good"
            comment.copy(
              isGood = if (isGoodClick) response.active else false,
              isBad = if (!isGoodClick) response.active else false,
              comment = comment.comment.copy(
                commentGoodCount = response.goodCount,
                commentBadCount = response.badCount
              )
            )
          } else comment
        })
      }
    )
  }

  def reportComment(commentId: Long): Unit =
    ReportModal.show(
      ReportModal.ReportConfig(
        contentTypeId = "4",
        contentId = commentId,
        contentDescription = "このコメント"
      )
    )

  def reportThread(threadId: Long): Unit =
    ReportModal.show(
      ReportModal.ReportConfig(
        contentTypeId = "3",
        contentId = threadId,
        contentDescription = "このスレッド"
      )
    )

  def updateThreadTitle(threadId: Long, title: String): Unit = {
    val request = ThreadUpdateRequest(threadTitle = title)
    util.ErrorHandler.handle(
      ThreadDetailApi.updateThread(threadId, request),
      onSuccess = { updated =>
        threadVar.set(Some(updated))
        EditModal.close()
      },
      showSuccessModal = true,
      successMessage = "スレッドを更新しました"
    )
  }

  def deleteThread(thread: ThreadViewValue): Unit =
    ThreadDetailApi.deleteThread(thread.threadId).foreach { _ =>
      TabStateManager.removeThreadPage(thread.threadId)
      Router.router.replaceState(
        WordDetailPage(thread.wordId, thread.tagId, WordDetailTab.Thread)
      )
    }

  def fetchThreadDetail(threadId: Long, force: Boolean = false): Unit = {
    if (!force && !state.needsInitialLoad(threadId)) return

    commentsLoadingVar.set(true)
    commentsHasMoreVar.set(true)
    util.ErrorHandler.handle(
      ThreadDetailApi.fetchComments(threadId),
      onSuccess = { comments =>
        commentsVar.set(comments)
        commentsLoadingVar.set(false)
        state.markLoaded(threadId)
        commentsHasMoreVar.set(comments.length >= ThreadDetailApi.PageSize)

        ThreadDetailApi.fetchThread(threadId).foreach { response =>
          threadVar.set(Some(response.thread))
        }
      },
      customErrorHandler = Some { error =>
        commentsLoadingVar.set(false)
        error match {
          case notFound: util.NotFoundError =>
            Modal.show(
              "スレッドが見つかりません",
              notFound.message,
              ModalType.Info,
              Router.router.pushState(HomePage)
            )
          case other => util.ErrorHandler.handleError(other)
        }
      }
    )
  }

  def fetchMoreComments(threadId: Long): Unit = {
    if (commentsLoadingVar.now() || !commentsHasMoreVar.now()) return

    commentsVar.now().lastOption.flatMap(_.comment.commentId).foreach { lastId =>
      commentsLoadingVar.set(true)
      ThreadDetailApi.fetchComments(threadId, Some(lastId)).foreach { newComments =>
        commentsVar.update(_ ++ newComments)
        commentsLoadingVar.set(false)
        commentsHasMoreVar.set(newComments.length >= ThreadDetailApi.PageSize)
      }
    }
  }

  def confirmBestAnswer(threadId: Long, commentId: Long, message: String): Unit =
    Modal.show(
      "ベストアンサーの確定",
      message,
      ModalType.Confirm,
      selectBestAnswer(threadId, commentId)
    )

  def updateComment(commentId: Long, newText: String): Unit = {
    val trimmed = util.Validation.trimAll(newText)
    if (!util.Validation.notBlank(trimmed)) {
      Modal.show("エラー", "空白のみのコメントは保存できません", ModalType.Info)
      return
    }

    val request = CommentUpdateByTextRequest(commentText = trimmed)
    util.ErrorHandler.handle(
      ThreadDetailApi.updateComment(commentId, request),
      onSuccess = { updated =>
        commentsVar.update(_.map { comment =>
          if (comment.comment.commentId.contains(commentId)) updated else comment
        })
        EditModal.close()
      },
      showSuccessModal = true,
      successMessage = "コメントを更新しました"
    )
  }

  def deleteComment(commentId: Long): Unit =
    ThreadDetailApi.deleteComment(commentId).foreach { _ =>
      commentsVar.update(_.filterNot(_.comment.commentId.contains(commentId)))
    }

  def commentPosted(comment: CommentViewValue): Unit = {
    commentsVar.update(comment +: _)
    threadVar.update(_.map(thread => thread.copy(commentCount = thread.commentCount + 1)))
  }

  private def selectBestAnswer(threadId: Long, commentId: Long): Unit = {
    val request = ResolveThreadRequest(threadId, commentId)
    util.ErrorHandler.handle(
      ThreadDetailApi.resolveThread(request),
      onSuccess = { _ =>
        threadVar.update(_.map { thread =>
          thread.copy(questionStatus =
            thread.questionStatus.map(
              _.copy(
                isResolved = true,
                resolvedDate = Some(js.Date.now().toString),
                bestCommentId = Some(commentId)
              )
            )
          )
        })
        commentsVar.update(_.map { comment =>
          comment.copy(isBestAnswer = comment.comment.commentId.contains(commentId))
        })
        Modal.show("ベストアンサー選出", "このスレッドを解決済みにしました！", ModalType.Info)
      }
    )
  }
}
