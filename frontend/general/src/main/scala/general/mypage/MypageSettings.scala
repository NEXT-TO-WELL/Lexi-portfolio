package general.mypage

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import general.ui.EditModal
import general.auth.AuthStorage
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

object MypageSettings {
  def showNameEditor(currentName: String, refreshProfile: () => Unit): Unit =
    EditModal.show(
      EditModal.EditConfig(
        title = "表示名を変更",
        fieldLabel = "新しい表示名",
        initialValue = currentName,
        maxLength = 50,
        onSave = newName => updateName(currentName, newName, refreshProfile)
      )
    )

  def passwordModal(isOpen: Var[Boolean]): HtmlElement = {
    val currentPasswordVar = Var("")
    val newPasswordVar = Var("")
    val confirmPasswordVar = Var("")
    val errorMessageVar = Var("")

    modalFrame("パスワードを変更", isOpen, destructive = false)(
      passwordField("現在のパスワード", "現在のパスワードを入力", currentPasswordVar),
      passwordField("新しいパスワード", "新しいパスワード（6文字以上）", newPasswordVar),
      passwordField("新しいパスワード（確認）", "もう一度入力", confirmPasswordVar),
      errorMessage(errorMessageVar),
      actionButtons(
        isOpen,
        "変更",
        destructive = false,
        submit = () => updatePassword(currentPasswordVar, newPasswordVar, confirmPasswordVar, errorMessageVar, isOpen)
      )
    )
  }

  def deleteAccountModal(isOpen: Var[Boolean]): HtmlElement = {
    val passwordVar = Var("")
    val confirmTextVar = Var("")
    val errorMessageVar = Var("")

    modalFrame("アカウントを削除", isOpen, destructive = true)(
      div(
        cls := "mb-4 p-4 bg-red-50 dark:bg-red-900/20 rounded-xl border border-red-200 dark:border-red-800",
        p(cls := "text-sm text-red-700 dark:text-red-400 font-semibold mb-2", "警告: この操作は取り消せません"),
        p(cls := "text-xs text-red-600 dark:text-red-400", "アカウントを削除すると、すべての投稿、コメント、お気に入りなどのデータが完全に削除されます。")
      ),
      passwordField("パスワードを入力", "パスワードを入力", passwordVar),
      div(
        cls := "mb-4",
        label(cls := "block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2", "確認のため「削除する」と入力"),
        input(
          tpe := "text",
          cls := inputClasses,
          placeholder := "削除する",
          controlled(value <-- confirmTextVar.signal, onInput.mapToValue --> confirmTextVar)
        )
      ),
      errorMessage(errorMessageVar),
      actionButtons(
        isOpen,
        "アカウントを削除",
        destructive = true,
        disabledSignal = Some(confirmTextVar.signal.map(_ != "削除する")),
        submit = () => requestAccountDeletion(passwordVar, confirmTextVar, errorMessageVar, isOpen)
      )
    )
  }

  private def updateName(currentName: String, newName: String, refreshProfile: () => Unit): Unit = {
    val trimmedName = util.Validation.trimAll(newName)
    if (!util.Validation.notBlank(newName)) {
      EditModal.isSubmitting.set(false)
      Modal.show("エラー", "表示名に空白のみは使用できません", ModalType.Info)
    } else if (trimmedName == currentName) {
      EditModal.close()
      EditModal.isSubmitting.set(false)
    } else {
      MypageApi.updateName(trimmedName).onComplete {
        case scala.util.Success(_) =>
          EditModal.close()
          EditModal.isSubmitting.set(false)
          Modal.show("成功", "表示名を変更しました", ModalType.Info, refreshProfile())
        case scala.util.Failure(error) =>
          EditModal.close()
          EditModal.isSubmitting.set(false)
          Modal.show("エラー", s"変更に失敗しました: ${error.getMessage}", ModalType.Info)
      }
    }
  }

  private def updatePassword(
      currentPassword: Var[String],
      newPassword: Var[String],
      confirmation: Var[String],
      errorMessage: Var[String],
      isOpen: Var[Boolean]
  ): Unit = {
    val current = currentPassword.now()
    val next = newPassword.now()
    val confirmed = confirmation.now()

    if (current.isEmpty || next.isEmpty || confirmed.isEmpty) {
      errorMessage.set("すべての項目を入力してください")
    } else if (next.length < 6) {
      errorMessage.set("新しいパスワードは6文字以上で入力してください")
    } else if (next != confirmed) {
      errorMessage.set("新しいパスワードが一致しません")
    } else {
      MypageApi.updatePassword(current, next).onComplete {
        case scala.util.Success(_) =>
          isOpen.set(false)
          Modal.show("成功", "パスワードを変更しました", ModalType.Info)
        case scala.util.Failure(error) =>
          errorMessage.set(s"変更に失敗しました: ${error.getMessage}")
      }
    }
  }

  private def requestAccountDeletion(
      password: Var[String],
      confirmation: Var[String],
      errorMessage: Var[String],
      isOpen: Var[Boolean]
  ): Unit =
    if (password.now().isEmpty) {
      errorMessage.set("パスワードを入力してください")
    } else if (confirmation.now() != "削除する") {
      errorMessage.set("「削除する」と入力してください")
    } else {
      isOpen.set(false)
      Modal.show(
        "最終確認",
        "本当にアカウントを削除しますか？この操作は取り消せません。",
        ModalType.Confirm,
        deleteAccount(password.now(), errorMessage, isOpen)
      )
    }

  private def deleteAccount(password: String, errorMessage: Var[String], isOpen: Var[Boolean]): Unit =
    MypageApi.deleteAccount(password).onComplete {
      case scala.util.Success(_) =>
        AuthStorage.logout()
        dom.window.location.href = "/"
      case scala.util.Failure(error) =>
        errorMessage.set(s"削除に失敗しました: ${error.getMessage}")
        isOpen.set(true)
    }

  private def modalFrame(
      title: String,
      isOpen: Var[Boolean],
      destructive: Boolean
  )(content: Modifier[HtmlElement]*): HtmlElement =
    div(
      cls := "fixed inset-0 z-[200] flex items-center justify-center bg-black/50",
      onClick --> (_ => isOpen.set(false)),
      div(
        cls := "bg-white dark:bg-gray-900 rounded-2xl p-6 w-full max-w-md mx-4 shadow-2xl",
        onClick --> (_.stopPropagation()),
        h3(
          cls := (if (destructive) "text-xl font-bold text-red-600 dark:text-red-400 mb-4"
                  else "text-xl font-bold text-gray-900 dark:text-gray-100 mb-4"),
          title
        ),
        content
      )
    )

  private def passwordField(labelText: String, placeholderText: String, valueVar: Var[String]): HtmlElement =
    div(
      cls := "mb-4",
      label(cls := "block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2", labelText),
      input(
        tpe := "password",
        cls := inputClasses,
        placeholder := placeholderText,
        controlled(value <-- valueVar.signal, onInput.mapToValue --> valueVar)
      )
    )

  private def errorMessage(messageVar: Var[String]): HtmlElement =
    div(
      child <-- messageVar.signal.map {
        case message if message.nonEmpty => div(cls := "mb-4 text-sm text-red-600 dark:text-red-400", message)
        case _                           => emptyNode
      }
    )

  private def actionButtons(
      isOpen: Var[Boolean],
      submitLabel: String,
      destructive: Boolean,
      disabledSignal: Option[Signal[Boolean]] = None,
      submit: () => Unit
  ): HtmlElement =
    div(
      cls := "flex gap-2",
      button(
        tpe := "button",
        cls := "flex-1 py-3 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded-xl font-bold hover:bg-gray-200 dark:hover:bg-gray-700",
        onClick --> (_ => isOpen.set(false)),
        "キャンセル"
      ),
      button(
        tpe := "button",
        cls := (if (destructive) {
                  "flex-1 py-3 bg-red-600 dark:bg-red-500 text-white rounded-xl font-bold hover:bg-red-700 dark:hover:bg-red-600 disabled:opacity-50 disabled:cursor-not-allowed"
                } else {
                  "flex-1 py-3 bg-blue-600 dark:bg-blue-500 text-white rounded-xl font-bold hover:bg-blue-700 dark:hover:bg-blue-600"
                }),
        disabledSignal.map(disabled <-- _),
        onClick --> (_ => submit()),
        submitLabel
      )
    )

  private val inputClasses =
    "w-full p-3 border border-gray-200 dark:border-gray-700 rounded-xl bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
}
