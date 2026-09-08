package general.ui

import com.raquo.laminar.api.L.*

object EditModal {

  case class EditConfig(
      title: String,
      fieldLabel: String,
      initialValue: String,
      maxLength: Int,
      onSave: String => Unit,
      initialIsJoke: Boolean = false,
      onSaveWithJoke: Option[(String, Boolean) => Unit] = None
  )

  case class EditModalState(
      isOpen: Boolean = false,
      config: EditConfig = EditConfig("", "", "", 1000, _ => ()),
      currentValue: String = "",
      currentIsJoke: Boolean = false
  )

  val stateVar = Var(EditModalState())
  val isSubmitting = Var(false)

  def show(config: EditConfig): Unit = {
    stateVar.set(EditModalState(true, config, config.initialValue, config.initialIsJoke))
    isSubmitting.set(false)
  }

  def close(): Unit = {
    stateVar.update(_.copy(isOpen = false))
  }

  def apply(): HtmlElement = {
    div(
      display <-- stateVar.signal.map(s => if (s.isOpen) "flex" else "none"),
      // fixed を absolute に変更し、スマホ枠内に制限
      cls := "absolute inset-0 z-[110] flex items-center justify-center bg-black/50 backdrop-blur-sm",

      // 背景オーバーレイ
      div(
        cls := "absolute inset-0",
        onClick --> { _ => close() }
      ),

      // モーダル本体
      div(
        // モバイルらしく角を大きく(rounded-3xl)、幅を枠に合わせる(max-w-[340px])
        cls := "relative w-full max-w-[340px] mx-4 bg-white dark:bg-gray-900 rounded-3xl shadow-2xl border border-transparent dark:border-gray-800 p-6 z-10 animate-in zoom-in-95 duration-200",
        onClick.stopPropagation --> { _ => () },

        h2(
          cls := "text-xl font-black text-gray-900 dark:text-gray-100 mb-4",
          child.text <-- stateVar.signal.map(_.config.title)
        ),

        div(
          cls := "mb-2 flex justify-between items-end",
          label(
            cls := "text-xs font-bold text-gray-400 dark:text-gray-500 ml-1",
            child.text <-- stateVar.signal.map(_.config.fieldLabel)
          ),
          span(
            cls := "text-[10px] font-medium text-gray-400 dark:text-gray-500 mr-1",
            child.text <-- stateVar.signal.map(s => s"${s.currentValue.length} / ${s.config.maxLength}")
          )
        ),

        textArea(
          cls := "w-full bg-gray-50 dark:bg-gray-800 border-none rounded-2xl px-4 py-3 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 outline-none transition-all resize-none",
          rows := 5,
          controlled(
            value <-- stateVar.signal.map(_.currentValue),
            onInput.mapToValue --> { v =>
              stateVar.update(s => s.copy(currentValue = v))
            }
          ),
          maxLength <-- stateVar.signal.map(_.config.maxLength),
          placeholder := "入力してください..."
        ),

        // ジョークトグル
        child <-- stateVar.signal.map { state =>
          if (state.config.onSaveWithJoke.isDefined) {
            div(
              cls := "flex items-center justify-between p-3 bg-orange-50/20 dark:bg-orange-900/20 rounded-xl border border-orange-100/80 dark:border-orange-800/50 mt-3",
              label(
                cls := "flex items-center cursor-pointer group flex-1",
                input(
                  tpe := "checkbox",
                  cls := "appearance-none relative w-11 h-6 bg-gray-200 dark:bg-gray-700 rounded-full cursor-pointer transition-colors checked:bg-orange-500 focus:outline-none before:content-[''] before:absolute before:top-[2px] before:left-[2px] before:bg-white before:w-5 before:h-5 before:rounded-full before:transition-transform checked:before:left-[calc(100%-1.25rem-2px)]",
                  controlled(
                    checked <-- stateVar.signal.map(_.currentIsJoke),
                    onClick.mapToChecked --> { isChecked =>
                      stateVar.update(s => s.copy(currentIsJoke = isChecked))
                    }
                  )
                ),
                span(cls := "ml-4 text-sm font-semibold text-gray-700 dark:text-gray-300", "この投稿はジョーク")
              )
            )
          } else {
            emptyNode
          }
        },

        div(
          cls := "flex flex-col space-y-3 mt-8",
          button(
            tpe := "button",
            cls := "w-full py-4 bg-blue-600 text-white font-bold rounded-2xl shadow-lg shadow-blue-100 dark:shadow-none hover:bg-blue-700 active:scale-[0.98] transition disabled:opacity-50",
            disabled <-- stateVar.signal.combineWith(isSubmitting.signal).map { case (state, submitting) =>
              submitting || state.currentValue.trim.isEmpty
            },
            child.text <-- isSubmitting.signal.map(submitting => if (submitting) "保存中..." else "保存する"),
            onClick --> { _ =>
              val state = stateVar.now()
              val text = state.currentValue.trim
              if (text.nonEmpty && !isSubmitting.now()) {
                isSubmitting.set(true)
                // onSaveWithJokeがあればそちらを、なければonSaveを呼び出す
                state.config.onSaveWithJoke match {
                  case Some(saveWithJoke) => saveWithJoke(text, state.currentIsJoke)
                  case None               => state.config.onSave(text)
                }
              }
            }
          ),
          button(
            tpe := "button",
            cls := "w-full py-2 text-gray-400 dark:text-gray-500 font-bold text-sm",
            "キャンセル",
            disabled <-- isSubmitting.signal,
            onClick --> { _ => close() }
          )
        )
      )
    )
  }
}
