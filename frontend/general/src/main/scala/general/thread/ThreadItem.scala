package general.thread

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import general.ui.EditModal
import general.ui.DropdownMenu
import general.navigation.Router
import general.quote.ContentQuoteCard
import models.*
import org.scalajs.dom
import scala.scalajs.js

object ThreadItem {
  def threadItem(
      t: ThreadViewValue,
      isMyPost: Boolean = false,
      onDelete: Option[Long => Unit] = None,
      onEdit: Option[(Long, String) => Unit] = None,
      onReport: Option[Long => Unit] = None,
      clickableCard: Boolean = true // ★追加: デフォルトは言葉詳細用の全体タップ
  ): HtmlElement = {
    val dateStr = new js.Date(t.threadCreateDate.toDouble).toLocaleDateString()

    val (typeLabel, typeIconPath, borderClass, bgClass, labelColor) = t.threadType.threadTypeId match {
      case "1" =>
        (
          "議論",
          "M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z",
          "border-purple-200 dark:border-gray-700",
          "bg-purple-50/30 dark:bg-gray-900",
          "bg-purple-600"
        )
      case "2" =>
        (
          "質問",
          "M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
          "border-orange-200 dark:border-gray-700",
          "bg-white dark:bg-gray-900",
          "bg-orange-500"
        )
      case "3" =>
        (
          "雑談",
          "M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z",
          "border-blue-200 dark:border-gray-700",
          "bg-blue-50/30 dark:bg-gray-900",
          "bg-blue-500"
        )
      case "4" =>
        (
          "共有",
          "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z",
          "border-emerald-200 dark:border-gray-700",
          "bg-emerald-50/30 dark:bg-gray-900",
          "bg-emerald-600"
        )
      case _ =>
        (
          "その他",
          "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
          "border-slate-200 dark:border-gray-700",
          "bg-slate-50/30 dark:bg-gray-900",
          "bg-slate-500"
        )
    }

    div(
      // ★ clickableCard が true の時だけ cursor-pointer を付与
      cls := s"relative p-4 mb-3 border-2 rounded-2xl flex flex-col hover:shadow-md transition-all $borderClass $bgClass ${
          if (clickableCard) "cursor-pointer active:scale-[0.98]" else ""
        }",

      // ★ clickableCard が true の時だけ全体クリックで遷移
      if (clickableCard) onClick --> { _ => Router.router.pushState(ThreadDetailPage(t.threadId)) } else emptyMod,

      div(
        cls := "flex items-start space-x-3",
        div(
          cls := s"flex-shrink-0 w-12 h-12 rounded-xl $labelColor flex flex-col items-center justify-center text-white shadow-sm p-2",
          svg.svg(
            svg.cls := "w-7 h-7",
            svg.fill := "none",
            svg.stroke := "currentColor",
            svg.strokeWidth := "2",
            svg.viewBox := "0 0 24 24",
            svg.path(svg.d := typeIconPath)
          ),
          span(cls := "text-[9px] font-bold mt-0.5", typeLabel)
        ),

        div(
          cls := "flex-grow min-w-0 space-y-2 flex-1",
          div(
            cls := "text-base font-bold text-gray-800 dark:text-gray-100 leading-tight break-words pr-8",
            t.threadTitle
          ),
          div(
            cls := "flex items-center space-x-2 text-[11px] text-gray-500 font-medium",
            span(
              // ★ isMyPost が true の時、文字色を青くし、太字にする
              cls := (if (isMyPost) "text-blue-600 dark:text-blue-400 font-bold flex items-center"
                      else "text-gray-500 dark:text-gray-400 flex items-center"),

              // アイコンの色も文字色に追従させる（fill := "currentColor" なので親の cls で変わります）
              svg.svg(
                svg.cls := "inline w-3 h-3 mr-1",
                svg.fill := "currentColor",
                svg.viewBox := "0 0 24 24",
                svg.path(
                  svg.d := "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"
                )
              ),

              // 名前とdisplayIDを表示
              t.displayId.fold(t.userName)(id => s"${t.userName} @$id")
            ),
            span("•"),
            span(dateStr)
          )
        ),

        div(
          cls := "flex-shrink-0 flex flex-col items-end",
          if (isMyPost) renderMenuButton(t.threadId, t.threadTitle, onDelete, onEdit)
          else if (onReport.isDefined) renderReportButton(t.threadId, onReport.get)
          else emptyNode
        )
      ),

      t.quote.map(quote => div(cls := "mt-3", ContentQuoteCard(quote))).getOrElse(emptyNode),

      // 解決ステータス
      t.questionStatus
        .map { status =>
          div(
            cls := "mt-2",
            if (status.isResolved)
              span(
                cls := "px-3 py-1 rounded-full text-[10px] font-bold bg-slate-700 text-white flex items-center w-fit gap-1",
                "解決済み"
              )
            else
              span(
                cls := "px-3 py-1 rounded-full text-[10px] font-bold bg-green-500 text-white animate-pulse flex items-center w-fit gap-1",
                "回答募集中"
              )
          )
        }
        .getOrElse(emptyNode),

      // 下部エリア
      div(
        cls := "flex items-center justify-between mt-4 pt-3 border-t border-gray-100 dark:border-gray-800",
        div(
          cls := "flex items-center space-x-1 text-[11px] text-gray-400 dark:text-gray-500",
          svg.svg(
            svg.cls := "w-4 h-4",
            svg.fill := "none",
            svg.stroke := "currentColor",
            svg.strokeWidth := "2",
            svg.viewBox := "0 0 24 24",
            svg.path(
              svg.d := "M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z"
            )
          ),
          span(cls := "font-bold", t.commentCount.toString)
        ),

        // ★ clickableCard が false (マイページ) の時だけリンクを表示
        if (!clickableCard) {
          button(
            cls := "text-[11px] font-bold text-blue-600 dark:text-blue-400 active:opacity-50 transition-opacity",
            "スレッドを見る →"
          )
        } else emptyNode
      )
    )
  }

  private def renderMenuButton(
      threadId: Long,
      threadTitle: String,
      onDelete: Option[Long => Unit],
      onEdit: Option[(Long, String) => Unit]
  ): HtmlElement = {
    div(
      cls := "absolute top-4 right-2 z-20",
      onClick.stopImmediatePropagation --> (_ => ()),
      (onDelete, onEdit) match {
        case (Some(deleteFn), Some(editFn)) =>
          DropdownMenu(
            Seq(
              DropdownMenu.MenuItem(
                "編集",
                () => {
                  EditModal.show(
                    EditModal.EditConfig(
                      title = "スレッドを編集",
                      fieldLabel = "タイトル",
                      initialValue = threadTitle,
                      maxLength = 100,
                      onSave = (newTitle: String) => editFn(threadId, newTitle)
                    )
                  )
                }
              ),
              DropdownMenu.MenuItem(
                "削除",
                () => {
                  Modal.show(
                    "削除の確認",
                    "このスレッドを削除してもよろしいですか？",
                    ModalType.Confirm,
                    deleteFn(threadId)
                  )
                },
                isDestructive = true
              )
            )
          )
        case _ =>
          button(
            tpe := "button",
            cls := "p-1 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-full transition-colors",
            onClick.stopImmediatePropagation --> (_ => dom.window.alert("メニューは利用できません")),
            svg.svg(
              svg.cls := "w-5 h-5",
              svg.fill := "currentColor",
              svg.viewBox := "0 0 24 24",
              svg.circle(svg.cx := "12", svg.cy := "5", svg.r := "2"),
              svg.circle(svg.cx := "12", svg.cy := "12", svg.r := "2"),
              svg.circle(svg.cx := "12", svg.cy := "19", svg.r := "2")
            )
          )
      }
    )
  }

  private def renderReportButton(threadId: Long, onReport: Long => Unit): HtmlElement = {
    div(
      cls := "absolute top-4 right-2 z-20",
      DropdownMenu(
        Seq(
          DropdownMenu.MenuItem("報告", () => onReport(threadId), isDestructive = false)
        )
      )
    )
  }
}
