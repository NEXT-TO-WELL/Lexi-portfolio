package general.ui

import com.raquo.laminar.api.L.*

object DropdownMenu {

  case class MenuItem(label: String, onClick: () => Unit, isDestructive: Boolean = false)

  def apply(items: Seq[MenuItem]): HtmlElement = {
    val isOpen = Var(false)

    div(
      cls := "relative inline-block",

      // トリガーボタン（三点リーダー）
      button(
        cls := "p-1 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-full transition-colors",
        onClick.stopPropagation --> { _ =>
          isOpen.update(!_)
        },
        svg.svg(
          svg.cls := "w-5 h-5",
          svg.fill := "currentColor",
          svg.viewBox := "0 0 24 24",
          svg.circle(svg.cx := "12", svg.cy := "5", svg.r := "2"),
          svg.circle(svg.cx := "12", svg.cy := "12", svg.r := "2"),
          svg.circle(svg.cx := "12", svg.cy := "19", svg.r := "2")
        )
      ),

      // ドロップダウンメニュー（縦並び、文字は横書き）
      div(
        display <-- isOpen.signal.map(if (_) "block" else "none"),
        cls := "absolute right-0 top-full mt-1 bg-white dark:bg-gray-900 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 py-1 z-50 min-w-[120px]",

        items.map { item =>
          button(
            cls := s"w-full text-left px-4 py-2 text-sm hover:bg-gray-50 dark:hover:bg-gray-800 transition whitespace-nowrap ${
                if (item.isDestructive) "text-red-600 dark:text-red-400" else "text-gray-700 dark:text-gray-200"
              }",
            styleAttr := "writing-mode: horizontal-tb;",
            onClick.stopPropagation --> { _ =>
              item.onClick()
              isOpen.set(false)
            },
            item.label
          )
        }
      ),

      // 背景クリックで閉じる
      child <-- isOpen.signal.map { open =>
        if (open) {
          div(
            cls := "fixed inset-0 z-40",
            onClick --> { _ => isOpen.set(false) }
          )
        } else emptyNode
      }
    )
  }
}
