package general.mypage

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import general.ui.ReportModal
import general.ui.EditModal
import general.ui.DropdownMenu
import general.navigation.Router
import models.*

object MyPageWordItem {
  // カード共通のスタイル。メニューボタン用に relative を指定
  private val cardBaseCls =
    "bg-white dark:bg-gray-900 p-4 rounded-xl shadow-sm border border-gray-100 dark:border-gray-800 space-y-2 relative"

  def meaningCard(
      m: MyPageMeaningViewValue,
      onFavoriteToggle: Option[Boolean => Unit] = None,
      onDelete: Option[Long => Unit] = None,
      onEdit: Option[(Long, String, Boolean) => Unit] = None
  ): HtmlElement =
    render(
      word = m.wordMain,
      tag = m.tagName,
      text = m.meaningView.meaning.meaningText,
      label = "語釈",
      labelColor = "text-blue-600 dark:text-blue-400",
      isFav = m.favoriteId.isDefined,
      reactions = Seq(
        Option.when(m.meaningView.isGood)("Good"),
        Option.when(m.meaningView.isHelpful)("Helpful"),
        Option.when(m.meaningView.isFunny)("Funny")
      ).flatten,
      onFavToggle = onFavoriteToggle,
      wId = m.meaningView.meaning.wordId,
      tId = m.meaningView.meaning.tagId,
      initialTab = WordDetailTab.Meaning,
      contentId = m.meaningView.meaning.meaningId,
      contentType = "meaning",
      onDelete = onDelete,
      onEdit = onEdit,
      maxLength = 1000,
      isJoke = m.meaningView.meaning.isJoke
    )

  def exampleCard(
      e: MyPageExampleViewValue,
      onFavoriteToggle: Option[Boolean => Unit] = None,
      onDelete: Option[Long => Unit] = None,
      onEdit: Option[(Long, String, Boolean) => Unit] = None
  ): HtmlElement =
    render(
      word = e.wordMain,
      tag = e.tagName,
      text = s""""${e.exampleView.example.exampleText}"""",
      label = "用例",
      labelColor = "text-green-600",
      isFav = e.favoriteId.isDefined,
      reactions = Seq(
        Option.when(e.exampleView.isGood)("Good"),
        Option.when(e.exampleView.isHelpful)("Helpful"),
        Option.when(e.exampleView.isFunny)("Funny")
      ).flatten,
      onFavToggle = onFavoriteToggle,
      wId = e.exampleView.example.wordId,
      tId = e.exampleView.example.tagId,
      initialTab = WordDetailTab.Example,
      isItalic = true,
      contentId = e.exampleView.example.exampleId,
      contentType = "example",
      onDelete = onDelete,
      onEdit = onEdit,
      maxLength = 1000,
      isJoke = e.exampleView.example.isJoke
    )

  private def render(
      word: String,
      tag: String,
      text: String,
      label: String,
      labelColor: String,
      isFav: Boolean,
      reactions: Seq[String] = Nil,
      onFavToggle: Option[Boolean => Unit],
      wId: Long,
      tId: Long,
      initialTab: WordDetailTab,
      isItalic: Boolean = false,
      contentId: Option[Long] = None,
      contentType: String = "",
      onDelete: Option[Long => Unit] = None,
      onEdit: Option[(Long, String, Boolean) => Unit] = None,
      maxLength: Int = 1000,
      isJoke: Boolean = false
  ): HtmlElement = {
    div(
      cls := cardBaseCls,
      // 1. 三点リーダー（ドロップダウンメニュー）
      div(
        cls := "absolute top-4 right-2",
        (contentId, onDelete, onEdit) match {
          case (Some(id), Some(deleteFn), Some(editFn)) =>
            DropdownMenu(
              Seq(
                DropdownMenu.MenuItem(
                  "編集",
                  () => {
                    val cleanText = if (isItalic) {
                      // 用例の場合は引用符を除去
                      text.stripPrefix("\"").stripSuffix("\"")
                    } else {
                      text
                    }
                    EditModal.show(
                      EditModal.EditConfig(
                        title = s"${label}を編集",
                        fieldLabel = label,
                        initialValue = cleanText,
                        maxLength = maxLength,
                        onSave = (newText: String) => editFn(id, newText, false), // 後方互換性のため
                        initialIsJoke = isJoke,
                        onSaveWithJoke = Some((newText: String, newIsJoke: Boolean) => editFn(id, newText, newIsJoke))
                      )
                    )
                  }
                ),
                DropdownMenu.MenuItem(
                  "削除",
                  () => {
                    Modal.show(
                      "削除の確認",
                      s"この${label}を削除してもよろしいですか？",
                      ModalType.Confirm,
                      deleteFn(id)
                    )
                  },
                  isDestructive = true
                )
              )
            )
          case (Some(id), _, _) =>
            val contentTypeId = if (contentType == "meaning") "1" else "2"
            DropdownMenu(
              Seq(
                DropdownMenu.MenuItem(
                  "報告",
                  () => {
                    ReportModal.show(
                      ReportModal.ReportConfig(
                        contentTypeId = contentTypeId,
                        contentId = id,
                        contentDescription = s"この$label"
                      )
                    )
                  },
                  isDestructive = false
                )
              )
            )
          case _ => emptyNode
        }
      ),

      // 2. ヘッダー情報（右側にメニューがあるため pr-6 で余白確保）
      div(
        cls := "flex justify-between items-start pr-6",
        div(
          cls := "flex flex-col",
          span(cls := s"text-sm font-bold $labelColor", word),
          span(cls := "text-[10px] text-gray-400 dark:text-gray-500", s"#$tag")
        ),
        div(
          cls := "flex items-center gap-2",
          span(cls := "text-[10px] text-gray-300 dark:text-gray-600", label),
          // お気に入りボタン
          onFavToggle.map { toggle =>
            button(
              cls := s"transition active:scale-90 ${if (isFav) "text-yellow-400" else "text-gray-300"}",
              onClick.stopImmediatePropagation --> (_ => toggle(!isFav)),
              if (isFav) {
                svg.svg(
                  svg.cls := "w-5 h-5",
                  svg.fill := "currentColor",
                  svg.stroke := "currentColor",
                  svg.strokeWidth := "2",
                  svg.viewBox := "0 0 24 24",
                  svg.path(
                    svg.d := "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
                  )
                )
              } else {
                svg.svg(
                  svg.cls := "w-5 h-5",
                  svg.fill := "none",
                  svg.stroke := "currentColor",
                  svg.strokeWidth := "2",
                  svg.viewBox := "0 0 24 24",
                  svg.path(
                    svg.d := "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
                  )
                )
              }
            )
          }
        )
      ),

      // 3. 本文
      if (reactions.nonEmpty) {
        div(
          cls := "flex flex-wrap gap-1.5",
          reactions.map(renderReactionBadge)
        )
      } else emptyNode,

      // 4. 本文
      p(
        cls := s"text-gray-800 dark:text-gray-100 text-sm leading-relaxed ${
            if (isItalic) "italic text-gray-600 dark:text-gray-300" else ""
          }",
        text
      ),

      // 5. 言葉の詳細ページへのリンク
      div(
        cls := "pt-1 flex justify-end",
        button(
          cls := "text-[11px] font-bold text-blue-600 dark:text-blue-400 active:opacity-50",
          onClick --> (_ => Router.router.pushState(WordDetailPage(wId, tId, initialTab))),
          "言葉の詳細を見る →"
        )
      )
    )
  }

  private def renderReactionBadge(label: String): HtmlElement = {
    val clsByLabel = label match {
      case "Good" =>
        "bg-rose-50 dark:bg-rose-900/30 text-rose-600 dark:text-rose-300 border-rose-100 dark:border-rose-800"
      case "Helpful" =>
        "bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300 border-blue-100 dark:border-blue-800"
      case "Funny" =>
        "bg-amber-50 dark:bg-amber-900/30 text-amber-600 dark:text-amber-300 border-amber-100 dark:border-amber-800"
      case _ => "bg-gray-50 dark:bg-gray-800 text-gray-600 dark:text-gray-300 border-gray-100 dark:border-gray-700"
    }

    span(cls := s"inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-bold $clsByLabel", label)
  }
}
