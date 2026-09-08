package component

import com.raquo.laminar.api.L.*

// モダールの種類を定義
enum ModalType {
  case Confirm // 確認（はい／いいえ）
  case Info // 通知（OKのみ）
}

object Modal {
  // モダールの状態を一括管理するVar
  case class ModalState(
      isOpen: Boolean = false,
      title: String = "",
      message: String = "",
      modalType: ModalType = ModalType.Info,
      onConfirm: () => Unit = () => ()
  )

  val stateVar = Var(ModalState())

  // 外部から呼ぶためのユーティリティ
  def show(title: String, message: String, mType: ModalType, action: => Unit = ()): Unit = {
    stateVar.set(ModalState(true, title, message, mType, () => action))
  }

  def apply(): HtmlElement = {
    div(
      // ★ 修正1: fixed -> absolute に変更。親のスマホ枠（relative）を基準にします。
      // inset-0 により、親要素の上下左右いっぱいに広がります。
      display <-- stateVar.signal.map(s => if (s.isOpen) "block" else "none"),
      cls := "absolute inset-0 z-[100] overflow-hidden", // モーダル表示中は枠外スクロールを防止

      // 背景（オーバーレイ）
      div(
        // ★ 修正2: ここも absolute に。
        cls := "absolute inset-0 bg-black/40 backdrop-blur-sm transition-opacity",
        onClick --> { _ => stateVar.update(_.copy(isOpen = false)) }
      ),

      // モータル本体の配置
      div(
        // ★ 修正3: h-full を使い、枠内の中央に配置
        cls := "relative flex h-full items-center justify-center p-6",
        div(
          // w-full max-w-sm により、枠の幅（500px）よりも少し小さいサイズで表示
          cls := "w-full max-w-[320px] transform overflow-hidden rounded-3xl bg-white dark:bg-gray-900 p-6 shadow-2xl border border-transparent dark:border-gray-800 transition-all animate-in fade-in zoom-in-95 duration-200",

          h3(
            cls := "text-lg font-bold text-gray-900 dark:text-gray-100 mb-2 text-center",
            child.text <-- stateVar.signal.map(_.title)
          ),
          p(
            cls := "text-sm text-gray-500 dark:text-gray-400 mb-6 text-center leading-relaxed",
            child.text <-- stateVar.signal.map(_.message)
          ),

          div(
            cls := "flex space-x-3",
            // キャンセルボタン
            child <-- stateVar.signal.map { s =>
              if (s.modalType == ModalType.Confirm) {
                button(
                  cls := "flex-1 py-3 rounded-xl bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 font-bold text-sm hover:bg-gray-200 dark:hover:bg-gray-700 transition active:scale-95",
                  "キャンセル",
                  onClick --> { _ => stateVar.update(_.copy(isOpen = false)) }
                )
              } else emptyNode
            },
            // 実行ボタン
            button(
              cls := "flex-1 py-3 rounded-xl bg-blue-600 text-white font-bold text-sm hover:bg-blue-700 shadow-md shadow-blue-200 dark:shadow-none transition active:scale-95",
              child.text <-- stateVar.signal.map(s => if (s.modalType == ModalType.Confirm) "はい" else "OK"),
              onClick --> { _ =>
                stateVar.now().onConfirm()
                stateVar.update(_.copy(isOpen = false))
              }
            )
          )
        )
      )
    )
  }
}
