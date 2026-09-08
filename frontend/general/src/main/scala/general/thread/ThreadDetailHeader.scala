package general.thread

import general.auth.AuthStorage
import general.ui.DropdownMenu
import component.{Modal, ModalType}
import general.ui.EditModal

import com.raquo.laminar.api.L.*
import models.ThreadViewValue
import general.quote.ContentQuoteCard

object ThreadDetailHeader {
  final case class Actions(
      updateTitle: String => Unit,
      delete: () => Unit,
      report: Long => Unit
  )

  def apply(thread: ThreadViewValue, actions: Actions): HtmlElement = {
    val currentUserId = AuthStorage.getUserId
    val isGuest = AuthStorage.getToken.isEmpty
    val isOwnThread = currentUserId.exists(thread.userId.contains)

    div(
      cls := "p-4 relative",
      threadMenu(thread, isGuest, isOwnThread, actions),
      div(
        cls := "flex items-center space-x-2 mb-1",
        span(
          cls := "px-1.5 py-0.5 rounded text-[9px] font-bold bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300 uppercase",
          thread.threadType.threadTypeName
        ),
        span(
          cls := (if (isOwnThread) {
                    "text-[10px] text-blue-600 dark:text-blue-400 font-bold"
                  } else {
                    "text-[10px] text-gray-400 dark:text-gray-500"
                  }),
          thread.displayId.fold(s"by ${thread.userName}")(id => s"by ${thread.userName} @$id")
        )
      ),
      h1(
        cls := "text-lg font-bold text-gray-900 dark:text-gray-100 leading-tight pr-10",
        thread.threadTitle
      ),
      thread.quote.map(quote => div(cls := "mt-3", ContentQuoteCard(quote))).getOrElse(emptyNode)
    )
  }

  private def threadMenu(
      thread: ThreadViewValue,
      isGuest: Boolean,
      isOwnThread: Boolean,
      actions: Actions
  ): Modifier[HtmlElement] =
    if (!isGuest && isOwnThread) {
      div(
        cls := "absolute top-4 right-2 z-20",
        DropdownMenu(
          Seq(
            DropdownMenu.MenuItem("編集", () => showEditModal(thread, actions.updateTitle)),
            DropdownMenu.MenuItem(
              "削除",
              () =>
                Modal.show(
                  "削除の確認",
                  "このスレッドを削除してもよろしいですか？\n※関連するコメントもすべて削除されます。",
                  ModalType.Confirm,
                  actions.delete()
                ),
              isDestructive = true
            )
          )
        )
      )
    } else if (!isGuest) {
      div(
        cls := "absolute top-4 right-2 z-20",
        DropdownMenu(
          Seq(
            DropdownMenu.MenuItem("報告", () => actions.report(thread.threadId))
          )
        )
      )
    } else {
      emptyNode
    }

  private def showEditModal(thread: ThreadViewValue, updateTitle: String => Unit): Unit =
    EditModal.show(
      EditModal.EditConfig(
        title = "スレッドを編集",
        fieldLabel = "タイトル",
        initialValue = thread.threadTitle,
        maxLength = 100,
        onSave = updateTitle
      )
    )
}
