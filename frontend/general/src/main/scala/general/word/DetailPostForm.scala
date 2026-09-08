package general.word

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import general.ui.DraftStorage

object DetailPostForm {
  sealed trait PostType
  case object Thread extends PostType
  case object Meaning extends PostType
  case object Example extends PostType

  case class PostPayload(text: String, isJoke: Boolean, typeId: Option[String] = None)

  def apply(
      postType: PostType,
      draftContext: String,
      onPost: (PostPayload, () => Unit) => Unit,
      onCancel: () => Unit
  ): HtmlElement = {
    val draftName = postType match {
      case Thread  => "thread"
      case Meaning => "meaning"
      case Example => "example"
    }
    val textVar = Var(DraftStorage.loadText(draftName, draftContext))
    val isJokeVar = Var(false) // ジョーク用の状態
    val selectedTypeId = Var("3")

    val (title, placeholderText, isTextArea, maxLengthLimit) = postType match {
      case Thread  => ("スレッド作成", "タイトルを入力...", false, 100)
      case Meaning => ("語釈を投稿", "詳しい語釈や定義を入力...", true, 1000)
      case Example => ("用例を投稿", "例文を入力...", true, 1000)
    }

    div(
      cls := "space-y-3",
      // スレッドのみ：タイプバッジ
      if (postType == Thread) {
        div(
          cls := "flex flex-wrap gap-2 mb-1",
          renderTypeBadge("1", "議論", "bg-purple-600", selectedTypeId),
          renderTypeBadge("2", "質問", "bg-orange-500", selectedTypeId),
          renderTypeBadge("3", "雑談", "bg-blue-500", selectedTypeId),
          renderTypeBadge("4", "共有", "bg-emerald-600", selectedTypeId)
        )
      } else emptyNode,

      // 文字数カウンター
      div(
        cls := "flex justify-between items-center text-xs px-1",
        span(cls := "text-gray-400 dark:text-gray-500", title),
        span(
          cls <-- textVar.signal.map(text =>
            if (text.length > maxLengthLimit) "text-red-500 font-bold"
            else "text-gray-400 dark:text-gray-500"
          ),
          child.text <-- textVar.signal.map(text => s"${text.length} / $maxLengthLimit")
        )
      ),

      // 入力フィールド
      if (isTextArea) {
        textArea(
          cls := "w-full p-3 border border-gray-200 dark:border-gray-700 rounded-xl bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 outline-none text-sm",
          placeholder := placeholderText,
          rows := 5,
          maxLength := maxLengthLimit,
          controlled(
            value <-- textVar,
            onInput.mapToValue --> { text =>
              textVar.set(text)
              DraftStorage.saveText(draftName, draftContext, text)
            }
          )
        )
      } else {
        input(
          tpe := "text",
          cls := "w-full p-3 border border-gray-200 dark:border-gray-700 rounded-xl bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 outline-none text-sm",
          placeholder := placeholderText,
          maxLength := maxLengthLimit,
          controlled(
            value <-- textVar,
            onInput.mapToValue --> { text =>
              textVar.set(text)
              DraftStorage.saveText(draftName, draftContext, text)
            }
          )
        )
      },

      // 語釈と用例の時だけジョークトグルを表示
      if (postType == Meaning || postType == Example) {
        renderJokeToggle(isJokeVar, "この投稿はジョーク")
      } else emptyNode,

      // ボタンエリア
      div(
        dataAttr("detail-post-actions") := draftName,
        cls := "space-y-2 pt-1",
        div(
          cls := "grid grid-cols-2 gap-2",
          button(
            tpe := "button",
            cls <-- textVar.signal.map { text =>
              val trimmed = util.Validation.trimAll(text)
              if (trimmed.nonEmpty && trimmed.length <= maxLengthLimit)
                "min-w-0 whitespace-nowrap px-3 py-2.5 bg-blue-600 text-white font-bold rounded-xl shadow-md hover:bg-blue-700 active:scale-95 transition"
              else
                "min-w-0 whitespace-nowrap px-3 py-2.5 bg-gray-300 dark:bg-gray-700 text-gray-500 dark:text-gray-400 font-bold rounded-xl cursor-not-allowed"
            },
            disabled <-- textVar.signal.map { text =>
              val trimmed = util.Validation.trimAll(text)
              trimmed.isEmpty || trimmed.length > maxLengthLimit
            },
            "投稿する",
            onClick --> { _ =>
              val content = util.Validation.trimAll(textVar.now())
              if (content.isEmpty) {
                Modal.show("エラー", "内容を入力してください", ModalType.Info, {})
              } else if (content.length > maxLengthLimit) {
                Modal.show("エラー", s"${maxLengthLimit}文字以内で入力してください", ModalType.Info, {})
              } else {
                onPost(
                  PostPayload(content, isJokeVar.now(), if (postType == Thread) Some(selectedTypeId.now()) else None),
                  () => {
                    textVar.set("")
                    DraftStorage.discard(draftName, draftContext)
                  }
                )
              }
            }
          ),
          button(
            tpe := "button",
            cls := "min-w-0 whitespace-nowrap px-3 py-2.5 bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-300 font-bold rounded-xl",
            "キャンセル",
            onClick --> (_ => onCancel())
          )
        ),
        button(
          tpe := "button",
          cls := "mx-auto block w-fit whitespace-nowrap px-3 py-2 text-xs text-gray-400 hover:text-red-500 dark:hover:text-red-400",
          "下書きを破棄",
          onClick --> { _ =>
            Modal.show(
              "下書きを破棄",
              "入力中の内容は復元できなくなります。破棄しますか？",
              ModalType.Confirm, {
                textVar.set("")
                DraftStorage.discard(draftName, draftContext)
                onCancel()
              }
            )
          }
        )
      )
    )
  }

  private def renderJokeToggle(jokeVar: Var[Boolean], labelText: String) = div(
    cls := "flex items-center justify-between p-3 bg-orange-50/20 dark:bg-orange-900/20 rounded-xl border border-orange-100/80 dark:border-orange-800/50 mt-1",
    label(
      cls := "flex items-center cursor-pointer group flex-1",
      input(
        tpe := "checkbox",
        cls := "appearance-none relative w-11 h-6 bg-gray-200 dark:bg-gray-700 rounded-full cursor-pointer transition-colors checked:bg-orange-500 focus:outline-none before:content-[''] before:absolute before:top-[2px] before:left-[2px] before:bg-white before:w-5 before:h-5 before:rounded-full before:transition-transform checked:before:left-[calc(100%-1.25rem-2px)]",
        controlled(checked <-- jokeVar.signal, onClick.mapToChecked --> jokeVar)
      ),
      span(cls := "ml-4 text-sm font-semibold text-gray-700 dark:text-gray-300", labelText)
    )
  )

  private def renderTypeBadge(id: String, label: String, activeColor: String, selectedVar: Var[String]): HtmlElement = {
    button(
      tpe := "button",
      cls <-- selectedVar.signal.map(curr =>
        s"px-3 py-1 rounded-full text-[10px] font-bold transition ${
            if (curr == id) s"$activeColor text-white shadow-sm"
            else "bg-gray-100 dark:bg-gray-800 text-gray-400 dark:text-gray-500"
          }"
      ),
      label,
      onClick --> (_ => selectedVar.set(id))
    )
  }
}
