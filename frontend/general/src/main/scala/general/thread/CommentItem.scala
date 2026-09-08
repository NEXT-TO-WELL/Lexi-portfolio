package general.thread

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import general.ui.EditModal
import general.ui.DropdownMenu
import models.*
import general.quote.ContentQuoteCard
import org.scalajs.dom
import scala.scalajs.js

object CommentItem {
  sealed trait ReactionType
  case object Good extends ReactionType
  case object Bad extends ReactionType
  case object NoReaction extends ReactionType

  def apply(
      cv: CommentViewValue,
      depth: Int,
      replyToName: Option[String] = None,
      onReplyClick: Option[() => Unit] = None,
      onReactionToggle: (Long, String) => Unit,
      isReadOnly: Boolean = false,
      onReport: Option[Long => Unit] = None,
      isOwnComment: Boolean = false,
      // ★ 追加: 編集・削除用のコールバック
      onEdit: Option[String => Unit] = None,
      onDelete: Option[() => Unit] = None,
      showBestAnswerTrigger: Boolean = false,
      onSelectBestAnswer: Option[() => Unit] = None
  ): HtmlElement = {
    val displayUserName = cv.displayId.fold(cv.userName) { id =>
      s"${cv.userName} @$id"
    }

    val activeState = if (cv.isGood) Good else if (cv.isBad) Bad else NoReaction
    val gCount = cv.comment.commentGoodCount
    val bCount = cv.comment.commentBadCount

    val indentClass = depth match {
      case 0 => "ml-0"
      case 1 => "ml-5"
      case _ => "ml-10"
    }

    div(
      cls := s"relative flex space-x-3 $indentClass transition-all",
      // カードの基本スタイル
      cls <-- Val(if (cv.comment.parentCommentId.isEmpty) {
        "bg-transparent p-0 mb-0 pb-4"
      } else {
        val baseCard = "bg-white dark:bg-gray-900 border p-3 rounded-xl mb-2 shadow-sm "
        if (isOwnComment) baseCard + "border-blue-200 dark:border-blue-800"
        else baseCard + "border-gray-100 dark:border-gray-800"
      }),

      // ★ 修正：depthが2（孫コメント以降）の時だけ「枝」を表示
      if (depth >= 2) {
        div(
          // カードの左側から外へ伸びる横線
          cls := "absolute left-[-16px] top-7 w-[16px] h-[1.5px] bg-gray-200"
        )
      } else emptyNode,

      // --- 三点リーダー配置の出し分け ---
      if ((isReadOnly || isOwnComment) && (onEdit.isDefined || onDelete.isDefined)) {
        div(
          cls := "absolute top-2 right-2 z-20",
          DropdownMenu(
            Seq(
              DropdownMenu.MenuItem(
                "編集",
                () => {
                  EditModal.show(
                    EditModal.EditConfig(
                      title = "コメントを編集",
                      fieldLabel = "内容",
                      initialValue = cv.comment.commentText,
                      maxLength = 400,
                      onSave = text => {
                        onEdit.foreach(_(text))
                        // 保存処理後にモーダルを閉じる等の制御は呼び出し側 or EditModal内で行う
                      }
                    )
                  )
                }
              ),
              DropdownMenu.MenuItem(
                "削除",
                () => {
                  Modal.show(
                    "削除の確認",
                    "このコメントを削除してもよろしいですか？",
                    ModalType.Confirm,
                    // 削除実行
                    action = { onDelete.foreach(_()) }
                  )
                },
                isDestructive = true
              )
            )
          )
        )
      } else if (onReport.isDefined && !isOwnComment) {
        // 自分以外のコメントかつ報告コールバックがある場合
        renderReportButton(cv.comment.commentId.getOrElse(-1L), onReport.get, top = "top-2", right = "right-2")
      } else {
        emptyNode
      },

      div(
        cls := s"flex-shrink-0 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-gray-500 dark:text-gray-400 ${
            if (depth > 0) "w-6 h-6 text-[10px]" else "w-8 h-8 text-xs"
          }",
        svg.svg(
          svg.cls := "w-full h-full",
          svg.fill := "currentColor",
          svg.viewBox := "0 0 24 24",
          svg.path(
            svg.d := "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"
          )
        )
      ),
      div(
        cls := "flex-grow space-y-1 min-w-0 pr-10",
        div(
          cls := "flex items-center space-x-2 text-xs",
          // ユーザー名がメニューと重ならないよう右余白を確保
          span(
            cls := "truncate",
            // 自分のコメントなら青く太字に
            cls <-- Val(
              if (isOwnComment) "text-blue-600 dark:text-blue-400 font-bold"
              else "text-gray-700 dark:text-gray-200 font-bold"
            ),
            displayUserName
          ),
          if (depth >= 2 && replyToName.isDefined)
            span(cls := "text-[10px] text-blue-500 dark:text-blue-400 font-medium", s"to @${replyToName.get}")
          else emptyNode,
          if (cv.isBestAnswer)
            span(
              cls := "flex items-center gap-1 text-[9px] bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-300 px-1.5 py-0.5 rounded font-bold",
              svg.svg(
                svg.cls := "w-3 h-3 fill-amber-600 dark:fill-amber-300",
                svg.viewBox := "0 0 24 24",
                svg.path(
                  svg.d := "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"
                )
              ),
              "BEST"
            )
          else emptyNode
        ),
        cv.quote.map(quote => ContentQuoteCard(quote)).getOrElse(emptyNode),
        div(cls := "text-sm text-gray-800 dark:text-gray-100 leading-relaxed break-words", cv.comment.commentText),
        div(
          cls := "flex items-center justify-between mt-2",
          span(
            cls := "text-[9px] text-gray-300 dark:text-gray-600",
            new js.Date(cv.comment.commentDate.toDouble).toLocaleString()
          ),
          if (isReadOnly) {
            button(
              cls := "text-[11px] font-bold text-blue-600 dark:text-blue-400 active:scale-95 transition-transform",
              "ログインして返信",
              onClick --> { _ => dom.window.location.href = "/signIn" }
            )
          } else {
            div(
              cls := "flex items-center space-x-4",
              if (showBestAnswerTrigger) {
                button(
                  cls := "flex items-center space-x-1 text-[10px] font-bold text-amber-500 dark:text-amber-400 border border-amber-200 dark:border-amber-800 bg-amber-50/50 dark:bg-amber-900/20 px-2 py-1 rounded-md active:scale-95 transition-all",
                  svg.svg(
                    svg.cls := "w-3 h-3",
                    svg.fill := "none",
                    svg.stroke := "currentColor",
                    svg.strokeWidth := "3",
                    svg.viewBox := "0 0 24 24",
                    svg.path(svg.d := "M5 13l4 4L19 7")
                  ),
                  span("ベスト選出"),
                  onClick --> { _ => onSelectBestAnswer.foreach(_()) }
                )
              } else emptyNode,
              renderReactionBtn(
                "good",
                Good,
                activeState,
                gCount,
                cv.comment.commentId.getOrElse(-1L),
                onReactionToggle,
                isReadOnly
              ),
              renderReactionBtn(
                "bad",
                Bad,
                activeState,
                bCount,
                cv.comment.commentId.getOrElse(-1L),
                onReactionToggle,
                isReadOnly
              ),
              onReplyClick
                .map(action =>
                  button(
                    cls := "text-[10px] font-bold text-gray-400 dark:text-gray-500 hover:text-blue-500 dark:hover:text-blue-400 transition",
                    "返信",
                    onClick --> (_ => action())
                  )
                )
                .getOrElse(emptyNode)
            )
          }
        )
      )
    )
  }

  // マイページ用メニュー
  private def renderMenuButton(top: String, right: String): HtmlElement = {
    button(
      cls := s"absolute $top $right p-1 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-full z-20 transition-colors",
      onClick.stopImmediatePropagation --> { _ =>
        dom.window.alert("コメントの編集・削除メニュー")
      },
      threeDotsIcon
    )
  }

  // 報告用メニュー（位置を引数で調整可能に）
  private def renderReportButton(commentId: Long, onReport: Long => Unit, top: String, right: String): HtmlElement = {
    div(
      cls := s"absolute $top $right z-20",
      DropdownMenu(
        Seq(
          DropdownMenu.MenuItem("報告", () => onReport(commentId), isDestructive = false)
        )
      )
    )
  }

  private def threeDotsIcon = svg.svg(
    svg.cls := "w-5 h-5",
    svg.fill := "currentColor",
    svg.viewBox := "0 0 24 24",
    svg.circle(svg.cx := "12", svg.cy := "5", svg.r := "2"),
    svg.circle(svg.cx := "12", svg.cy := "12", svg.r := "2"),
    svg.circle(svg.cx := "12", svg.cy := "19", svg.r := "2")
  )

  private def renderReactionBtn(
      apiType: String,
      target: ReactionType,
      current: ReactionType,
      count: Int,
      id: Long,
      onToggle: (Long, String) => Unit,
      isReadOnly: Boolean
  ): HtmlElement = {
    val isActive = current == target
    val tag = if (isReadOnly) div else button

    // アイコンの定義
    val iconPath = apiType match {
      case "good" =>
        "M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-1a2 2 0 00-2 2v5"
      case "bad" =>
        "M10 14H5.236a2 2 0 01-1.789-2.894l3.5-7A2 2 0 018.737 3h4.018c.163 0 .326.02.485.06L17 4m-7 10v6a2 2 0 002 2h1a2 2 0 002-2v-5"
      case _ => ""
    }

    val activeStyle = apiType match {
      case "good" =>
        "bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300 ring-1 ring-blue-200 dark:ring-blue-800 scale-105 shadow-sm"
      case "bad" =>
        "bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-300 ring-1 ring-red-200 dark:ring-red-800 scale-105 shadow-sm"
      case _ => "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400"
    }

    val hoverBgClass = if (!isReadOnly) {
      if (isActive) {
        if (apiType == "good") "hover:bg-blue-100 dark:hover:bg-blue-900/40"
        else "hover:bg-red-100 dark:hover:bg-red-900/40"
      } else "hover:bg-gray-100 dark:hover:bg-gray-800"
    } else ""

    tag(
      if (!isReadOnly) tpe := "button" else emptyMod,
      cls := s"flex items-center space-x-1 text-[10px] font-bold transition-all duration-150 rounded-md px-2 py-1 ${
          if (isActive) activeStyle else "text-gray-400 dark:text-gray-500"
        } $hoverBgClass ${if (!isReadOnly) "active:scale-95 cursor-pointer group" else ""}",

      svg.svg(
        svg.cls := s"w-4 h-4 transition-transform duration-150 group-hover:-translate-y-0.5 ${
            if (isActive) "scale-110" else ""
          }",
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 24 24",
        svg.path(svg.d := iconPath)
      ),
      span(if (count > 0) count.toString else ""),
      if (!isReadOnly) onClick --> { _ => onToggle(id, apiType) } else emptyMod
    )
  }
}
