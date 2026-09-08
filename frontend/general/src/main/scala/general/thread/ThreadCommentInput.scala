package general.thread

import com.raquo.laminar.api.L.*
import general.ui.Toast
import general.ui.DraftStorage
import general.auth.AuthStorage
import models.{CommentPostRequest, CommentViewValue}
import models.ThreadViewValue
import general.quote.{ContentQuoteCard, ContentQuotePicker}
import shared.api.ContentQuoteReference
import org.scalajs.dom
import scala.scalajs.js

object ThreadCommentInput {
  def apply(
      threadId: Long,
      thread: Signal[Option[ThreadViewValue]],
      activeReplyParent: Var[Option[CommentViewValue]],
      replyTo: Var[Option[CommentViewValue]],
      onCommentPosted: CommentViewValue => Unit
  ): HtmlElement =
    if (AuthStorage.getToken.isDefined) {
      authenticatedForm(threadId, thread, activeReplyParent, replyTo, onCommentPosted)
    } else {
      signInPrompt()
    }

  private def authenticatedForm(
      threadId: Long,
      thread: Signal[Option[ThreadViewValue]],
      activeReplyParent: Var[Option[CommentViewValue]],
      replyTo: Var[Option[CommentViewValue]],
      onCommentPosted: CommentViewValue => Unit
  ): HtmlElement = {
    val draftContext = s"thread-$threadId"
    val commentTextVar = Var(DraftStorage.loadText("comment", draftContext))
    val selectedQuoteVar = Var(DraftStorage.loadQuote("comment", draftContext))
    val showQuotePickerVar = Var(false)
    var textAreaRef = Option.empty[dom.html.TextArea]

    div(
      display <-- activeReplyParent.signal.combineWith(replyTo.signal).map {
        case (Some(_), None) => "none"
        case _               => "block"
      },
      cls := "absolute bottom-0 left-0 right-0 bg-white/95 dark:bg-gray-900/95 backdrop-blur-sm border-t border-gray-100 dark:border-gray-800 p-2 pb-6 shadow-[0_-4px_20px_rgb(0,0,0,0.08)] z-40",
      child <-- replyTo.signal.map {
        case Some(comment) =>
          div(
            cls := "text-[10px] text-gray-500 dark:text-gray-400 mb-2 flex justify-between bg-blue-50 dark:bg-blue-900/20 p-2 rounded-lg mx-1",
            span(s"@${comment.userName} への返信"),
            button(
              "× 取消",
              cls := "font-bold text-blue-600 dark:text-blue-300",
              onClick --> (_ => replyTo.set(None))
            )
          )
        case None => emptyNode
      },
      child <-- selectedQuoteVar.signal.map {
        case Some(quote) =>
          div(
            cls := "mx-1 mb-2",
            ContentQuoteCard(
              quote,
              Some(() => {
                selectedQuoteVar.set(None)
                DraftStorage.saveQuote("comment", draftContext, None)
              })
            )
          )
        case None => emptyNode
      },
      child <-- showQuotePickerVar.signal.combineWith(thread).map {
        case (true, Some(value)) =>
          div(
            cls := "mx-1 mb-2",
            ContentQuotePicker(
              value.wordId,
              value.tagId,
              quote => {
                selectedQuoteVar.set(Some(quote))
                DraftStorage.saveQuote("comment", draftContext, Some(quote))
                showQuotePickerVar.set(false)
              }
            )
          )
        case _ => emptyNode
      },
      div(
        cls := "flex items-end space-x-2 px-1",
        textArea(
          cls := "flex-grow bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 rounded-xl p-2 px-4 text-sm focus:outline-none min-h-[36px] max-h-32 resize-none leading-normal",
          placeholder := "コメントを入力...",
          onMountCallback(context => textAreaRef = Some(context.thisNode.ref)),
          controlled(
            value <-- commentTextVar,
            onInput.mapToValue --> { text =>
              commentTextVar.set(text)
              DraftStorage.saveText("comment", draftContext, text)
            }
          ),
          onInput --> (_ => resizeTextArea(textAreaRef))
        ),
        button(
          tpe := "button",
          cls := "h-9 flex-shrink-0 rounded-lg border border-orange-200 px-2 text-xs font-bold text-orange-600 dark:border-orange-900 dark:text-orange-300",
          "引用",
          onClick --> (_ => showQuotePickerVar.update(!_))
        ),
        button(
          cls := "bg-blue-600 dark:bg-blue-500 text-white w-12 h-9 rounded-lg disabled:bg-gray-300 dark:disabled:bg-gray-700 disabled:text-white dark:disabled:text-gray-400 shadow-sm flex-shrink-0 flex items-center justify-center",
          disabled <-- commentTextVar.signal.map(_.trim.isEmpty),
          span(cls := "text-xs font-bold", "送信"),
          onClick --> { _ =>
            val parentId = replyTo
              .now()
              .flatMap(_.comment.commentId)
              .orElse(activeReplyParent.now().flatMap(_.comment.commentId))
            val quoteReference = selectedQuoteVar
              .now()
              .map(value => ContentQuoteReference(value.sourceContentTypeId, value.sourceContentId))
            val request = CommentPostRequest(threadId, parentId, commentTextVar.now(), quoteReference)

            util.ErrorHandler.handle(
              ThreadDetailApi.createComment(request),
              onSuccess = { posted =>
                commentTextVar.set("")
                DraftStorage.discard("comment", draftContext)
                replyTo.set(None)
                selectedQuoteVar.set(None)
                DraftStorage.saveQuote("comment", draftContext, None)
                showQuotePickerVar.set(false)
                resetTextArea(textAreaRef)
                onCommentPosted(posted)
                posted.comment.commentId.foreach(scrollToPostedComment)
              }
            )
          }
        )
      ),
      button(
        tpe := "button",
        cls := "mt-2 w-full text-xs text-gray-400 hover:text-red-500 dark:hover:text-red-400",
        "下書きを破棄",
        onClick --> { _ =>
          component.Modal.show(
            "下書きを破棄",
            "入力中の内容は復元できなくなります。破棄しますか？",
            component.ModalType.Confirm, {
              commentTextVar.set("")
              DraftStorage.discard("comment", draftContext)
              DraftStorage.saveQuote("comment", draftContext, None)
              resetTextArea(textAreaRef)
            }
          )
        }
      )
    )
  }

  private def signInPrompt(): HtmlElement =
    div(
      cls := "fixed bottom-[calc(4rem+env(safe-area-inset-bottom))] left-0 right-0 bg-white dark:bg-gray-900 border-t border-gray-100 dark:border-gray-800 p-4 z-40 shadow-md",
      button(
        cls := "w-full bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 p-3 rounded-xl text-sm font-bold text-center",
        "返信するにはログインが必要です",
        onClick --> (_ => dom.window.location.href = "/signIn")
      )
    )

  private def resizeTextArea(textAreaRef: Option[dom.html.TextArea]): Unit =
    textAreaRef.foreach { textArea =>
      textArea.style.height = "36px"
      textArea.style.height = s"${textArea.scrollHeight}px"
    }

  private def resetTextArea(textAreaRef: Option[dom.html.TextArea]): Unit =
    textAreaRef.foreach(_.style.height = "36px")

  private def scrollToPostedComment(commentId: Long): Unit = {
    dom.window.location.hash = s"comment-$commentId"
    Toast.show("コメントを投稿しました")
    js.timers.setTimeout(50) {
      Option(dom.document.getElementById(s"comment-$commentId")).foreach(_.scrollIntoView())
    }
  }
}
