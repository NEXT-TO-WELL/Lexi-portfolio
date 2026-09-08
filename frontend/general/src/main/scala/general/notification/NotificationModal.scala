package general.notification

import com.raquo.laminar.api.L.*
import general.navigation.Router
import general.notification.NotificationState
import general.ui.Toast
import models.{HomePage, Page, ThreadDetailPage, WordDetailPage, WordDetailTab}
import shared.api.{NotificationViewValue, NotificationsResponse}
import util.{ApiClient, ErrorHandler}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.Try

object NotificationModal {

  private val notificationsVar = Var(Option.empty[Seq[NotificationViewValue]])

  def apply(): HtmlElement = {
    val isMarkingAllReadVar = Var(false)
    val pushStateVar = Var[PushNotificationClient.State](PushNotificationClient.Loading)
    val hasUnreadSignal = notificationsVar.signal.map(_.exists(_.exists(!_.isRead)))

    // This modal should only be rendered if the user is logged in,
    // so we don't need the AuthStorage check here again.

    div(
      // Modal container with overlay
      cls := "absolute inset-0 z-[100] flex justify-end overflow-hidden",
      onMountCallback { _ =>
        notificationsVar.set(None)
        ApiClient.get[NotificationsResponse]("/notifications").foreach { res =>
          notificationsVar.set(Some(res.notifications))
        }
        NotificationState.refreshUnreadStatus()
        PushNotificationClient.loadState().foreach(pushStateVar.set)
      },

      // Overlay to close the modal
      div(
        cls := "absolute inset-0 bg-black/20",
        onClick --> { _ => NotificationState.isShowingVar.set(false) }
      ),

      // Modal content
      div(
        cls := "relative mt-16 mr-2 ml-2 w-full max-w-[360px] bg-white dark:bg-gray-900 rounded-2xl shadow-2xl border border-gray-100 dark:border-gray-800 animate-in fade-in-5 slide-in-from-top-4 duration-200 flex flex-col h-fit max-h-[80%]",
        div(
          cls := "p-4 border-b border-gray-100 dark:border-gray-800 flex items-center justify-between",
          h3(cls := "font-bold text-lg text-gray-900 dark:text-gray-100", "お知らせ"),
          div(
            cls := "flex items-center gap-1",
            button(
              idAttr := "mark-all-notifications-read",
              tpe := "button",
              cls <-- isMarkingAllReadVar.signal.combineWith(hasUnreadSignal).map { case (isMarking, hasUnread) =>
                val stateClass =
                  if (isMarking || !hasUnread) "cursor-not-allowed text-gray-300 dark:text-gray-600"
                  else
                    "text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 active:scale-95"
                s"rounded-lg px-2 py-1.5 text-xs font-semibold transition $stateClass"
              },
              disabled <-- isMarkingAllReadVar.signal.combineWith(hasUnreadSignal).map { case (isMarking, hasUnread) =>
                isMarking || !hasUnread
              },
              aria.label := "すべての通知を既読にする",
              child.text <-- isMarkingAllReadVar.signal.map(if (_) "処理中..." else "すべて既読"),
              onClick --> { _ => markAllAsRead(isMarkingAllReadVar) }
            ),
            button(
              tpe := "button",
              aria.label := "お知らせを閉じる",
              cls := "p-2 -mr-2 text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-full transition",
              onClick --> { _ => NotificationState.isShowingVar.set(false) },
              svg.svg(
                svg.cls := "w-6 h-6",
                svg.fill := "none",
                svg.stroke := "currentColor",
                svg.strokeWidth := "2",
                svg.viewBox := "0 0 24 24",
                svg.path(svg.d := "M6 18L18 6M6 6l12 12")
              )
            )
          )
        ),
        renderPushControl(pushStateVar),
        div(
          cls := "max-h-[75vh] overflow-y-auto",
          child <-- notificationsVar.signal.map {
            case None =>
              div(cls := "text-center py-12 text-gray-500 dark:text-gray-400", "読み込み中...")
            case Some(notifications) if notifications.isEmpty =>
              div(cls := "text-center py-12 text-gray-500 dark:text-gray-400", "新しいお知らせはありません。")
            case Some(notifications) =>
              renderGroupedNotifications(notifications)
          }
        )
      )
    )
  }

  private def renderPushControl(pushStateVar: Var[PushNotificationClient.State]): HtmlElement =
    div(
      idAttr := "push-notification-control",
      cls := "border-b border-gray-100 bg-stone-50 px-4 py-3 dark:border-gray-800 dark:bg-gray-900",
      child <-- pushStateVar.signal.map {
        case PushNotificationClient.Loading =>
          p(cls := "text-xs text-gray-500 dark:text-gray-400", "ブラウザ通知を確認しています...")
        case PushNotificationClient.Available =>
          pushSwitchRow(
            enabled = false,
            pending = false,
            statusLabel = "オフ",
            action = () => changePushState(pushStateVar, enable = true)
          )
        case PushNotificationClient.Enabling =>
          pushSwitchRow(
            enabled = false,
            pending = true,
            statusLabel = "オンにしています...",
            action = () => ()
          )
        case PushNotificationClient.Enabled =>
          pushSwitchRow(
            enabled = true,
            pending = false,
            statusLabel = "オン",
            action = () => changePushState(pushStateVar, enable = false)
          )
        case PushNotificationClient.Disabling =>
          pushSwitchRow(
            enabled = true,
            pending = true,
            statusLabel = "オフにしています...",
            action = () => ()
          )
        case PushNotificationClient.Denied =>
          pushMessage("通知が拒否されています。ブラウザのサイト設定から許可してください。")
        case PushNotificationClient.Unsupported =>
          pushMessage("このブラウザはWeb Pushに対応していません。")
        case PushNotificationClient.InstallRequired =>
          pushMessage("iPhone・iPadでは、Lexiをホーム画面に追加してから通知を有効にできます。")
        case PushNotificationClient.DisabledByServer =>
          pushMessage("ブラウザ通知は現在利用できません。")
        case PushNotificationClient.Failed =>
          pushControlRow(
            "ブラウザ通知の状態を確認できませんでした。",
            "再確認",
            "retry-push-notifications",
            () => {
              pushStateVar.set(PushNotificationClient.Loading)
              PushNotificationClient.loadState().foreach(pushStateVar.set)
            },
            quiet = true
          )
      }
    )

  private def pushMessage(message: String): HtmlElement =
    p(cls := "text-xs leading-relaxed text-gray-500 dark:text-gray-400", message)

  private def pushSwitchRow(
      enabled: Boolean,
      pending: Boolean,
      statusLabel: String,
      action: () => Unit
  ): HtmlElement =
    div(
      cls := "flex items-center justify-between gap-4",
      div(
        cls := "min-w-0",
        div(
          cls := "flex items-baseline gap-2",
          p(cls := "text-sm font-semibold text-gray-900 dark:text-gray-100", "ブラウザ通知"),
          span(
            idAttr := "push-notification-status",
            cls := (if (enabled)
                      "text-xs font-bold text-blue-700 dark:text-blue-300"
                    else "text-xs font-bold text-gray-500 dark:text-gray-400"),
            statusLabel
          )
        ),
        p(
          idAttr := "push-notification-description",
          cls := "mt-0.5 text-xs leading-relaxed text-gray-600 dark:text-gray-400",
          "返信やリアクションをこのブラウザで受け取ります。"
        )
      ),
      button(
        idAttr := "push-notification-switch",
        tpe := "button",
        role := "switch",
        aria.checked := enabled.toString,
        aria.busy := pending,
        aria.describedBy := "push-notification-description push-notification-status",
        aria.label := (if (enabled) "ブラウザ通知をオフにする" else "ブラウザ通知をオンにする"),
        dataAttr("push-state") := (if (pending) "pending" else if (enabled) "on" else "off"),
        disabled := pending,
        cls := s"group -m-2 shrink-0 rounded-full p-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#17324d] focus-visible:ring-offset-2 dark:focus-visible:ring-blue-400 dark:focus-visible:ring-offset-gray-900 ${
            if (pending) "cursor-wait" else "cursor-pointer"
          }",
        span(
          cls := s"relative block h-7 w-12 rounded-full transition-colors duration-200 ${
              if (enabled) "bg-[#17324d] dark:bg-blue-500" else "bg-gray-300 dark:bg-gray-600"
            } ${
              if (pending) "opacity-60"
              else "group-hover:ring-4 group-hover:ring-gray-200 dark:group-hover:ring-gray-700"
            }",
          span(
            cls := s"absolute top-1 block h-5 w-5 rounded-full bg-white shadow-sm transition-transform duration-200 ${
                if (enabled) "translate-x-6" else "translate-x-1"
              }"
          )
        ),
        onClick --> (_ => action())
      )
    )

  private def pushControlRow(
      message: String,
      buttonLabel: String,
      buttonId: String,
      action: () => Unit,
      quiet: Boolean = false
  ): HtmlElement =
    div(
      cls := "flex items-center justify-between gap-3",
      p(cls := "text-xs leading-relaxed text-gray-600 dark:text-gray-300", message),
      button(
        idAttr := buttonId,
        tpe := "button",
        cls := (if (quiet)
                  "shrink-0 rounded-lg border border-gray-300 px-2.5 py-1.5 text-xs font-semibold text-gray-600 transition hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-500 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800"
                else
                  "shrink-0 rounded-lg bg-[#17324d] px-2.5 py-1.5 text-xs font-semibold text-white transition hover:bg-[#254665] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#17324d] focus-visible:ring-offset-2 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white"),
        onClick --> (_ => action()),
        buttonLabel
      )
    )

  private def changePushState(pushStateVar: Var[PushNotificationClient.State], enable: Boolean): Unit = {
    pushStateVar.set(if (enable) PushNotificationClient.Enabling else PushNotificationClient.Disabling)
    val operation = if (enable) PushNotificationClient.enable() else PushNotificationClient.disable()
    operation.foreach(pushStateVar.set)
  }

  private def markAllAsRead(isMarkingAllReadVar: Var[Boolean]): Unit = {
    val hasUnread = notificationsVar.now().exists(_.exists(!_.isRead))
    if (!hasUnread || isMarkingAllReadVar.now()) return

    isMarkingAllReadVar.set(true)
    ErrorHandler.handle(
      ApiClient.post[ujson.Obj, ujson.Value]("/notifications/read-all", ujson.Obj()),
      _ => {
        notificationsVar.update(_.map(_.map(_.copy(isRead = true))))
        NotificationState.markAllReadLocally()
        isMarkingAllReadVar.set(false)
        Toast.show("すべてのお知らせを既読にしました")
      },
      customErrorHandler = Some { error =>
        isMarkingAllReadVar.set(false)
        ErrorHandler.handleError(error)
      }
    )
  }

  private def renderGroupedNotifications(notifications: Seq[NotificationViewValue]): HtmlElement = {
    val grouped = groupNotificationsByDate(notifications)
    div(
      grouped.map { case (header, group) =>
        div(
          h4(
            cls := "text-xs font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider px-4 py-2 bg-gray-50/70 dark:bg-gray-800/70",
            header
          ),
          ul(
            cls := "divide-y divide-gray-100 dark:divide-gray-800",
            group.map(renderNotificationItem)
          )
        )
      }
    )
  }

  private def renderNotificationItem(n: NotificationViewValue): HtmlElement = {
    val (iconPath, iconClass) = n.notificationType match {
      case "REACTION" =>
        (
          "M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-1a2 2 0 00-2 2v5",
          "bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300"
        )
      case "COMMENT" =>
        (
          "M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z",
          "bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-300"
        )
      case "FOLLOW" =>
        (
          "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z",
          "bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-300"
        )
      case "SYSTEM" =>
        (
          "M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87l.22.127c.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247c.275.476.194 1.08-.214 1.45l-.973.883c-.28.254-.415.629-.408 1.007v.255c-.007.378.128.753.408 1.007l.973.883c.408.37.49.974.214 1.45l-1.296 2.247a1.125 1.125 0 01-1.37.49l-1.217-.456c-.355-.133-.75-.072-1.076.124l-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.542-.56.94-1.11.94h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87l-.22-.127c-.325-.196-.72-.257-1.075-.124l-1.217.456a1.125 1.125 0 01-1.37-.49l-1.296-2.247a1.125 1.125 0 01.214-1.45l.973-.883c.28-.254.415-.629.408-1.007v-.255c.007-.378-.128-.753-.408-1.007l-.973-.883a1.125 1.125 0 01-.214-1.45L5.63 6.256A1.125 1.125 0 017 5.766l1.217.456c.355.133.75.072 1.075-.124l.22-.127c.332-.184.582-.496.644-.87l.213-1.281zM15 12a3 3 0 11-6 0 3 3 0 016 0z",
          "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400"
        )
      case _ =>
        (
          "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
          "bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300"
        )
    }

    val targetPage = parseLink(n.link)

    li(
      dataAttr("notification-read") := n.isRead.toString,
      dataAttr("notification-type") := n.notificationType,
      cls("bg-blue-50/50 dark:bg-blue-900/20") := !n.isRead,
      cls := "p-4 flex items-start space-x-4 hover:bg-gray-50/80 dark:hover:bg-gray-800/80 transition cursor-pointer",
      onClick --> { _ =>
        // 未読の場合は既読にする
        if (!n.isRead) {
          ApiClient.post[ujson.Obj, ujson.Value](s"/notifications/${n.notificationId}/read", ujson.Obj()).foreach { _ =>
            NotificationState.refreshUnreadStatus()
          }
        }
        Router.navigateTo(targetPage)
        NotificationState.isShowingVar.set(false)
      },
      div(
        cls := s"w-10 h-10 rounded-full flex items-center justify-center text-xl flex-shrink-0 $iconClass p-2",
        svg.svg(
          svg.cls := "w-full h-full",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "1.5",
          svg.viewBox := "0 0 24 24",
          svg.path(svg.d := iconPath)
        )
      ),
      div(
        cls := "flex-grow",
        p(
          cls := "text-sm text-gray-800 dark:text-gray-100 leading-normal",
          n.actorName.map(name => b(s"$name さんが")).getOrElse(emptyNode),
          span(n.message.stripPrefix(n.actorName.getOrElse("") + " さんが"))
        ),
        p(cls := "text-xs text-gray-500 dark:text-gray-400 mt-1", new js.Date(n.date.toDouble).toLocaleString())
      )
    )
  }

  private def parseLink(link: String): Page = {
    link.split("/").toList.filter(_.nonEmpty) match {
      case "threads" :: id :: Nil =>
        Try(id.toLong).map(ThreadDetailPage.apply).getOrElse(HomePage)
      case "word" :: wordId :: "tag" :: tagId :: Nil =>
        (Try(wordId.toLong).toOption, Try(tagId.toLong).toOption) match {
          case (Some(wId), Some(tId)) => WordDetailPage(wId, tId)
          case _                      => HomePage
        }
      case "word" :: wordId :: "tag" :: tagId :: tab :: Nil =>
        (Try(wordId.toLong).toOption, Try(tagId.toLong).toOption) match {
          case (Some(wId), Some(tId)) =>
            val initialTab = tab match {
              case "example" => WordDetailTab.Example
              case "thread"  => WordDetailTab.Thread
              case _         => WordDetailTab.Meaning
            }
            WordDetailPage(wId, tId, initialTab)
          case _ => HomePage
        }
      case _ => HomePage
    }
  }

  private def groupNotificationsByDate(
      notifications: Seq[NotificationViewValue]
  ): List[(String, Seq[NotificationViewValue])] = {
    val now = new js.Date()
    val todayStart = new js.Date(now.getFullYear().toInt, now.getMonth().toInt, now.getDate().toInt).getTime()
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000

    val grouped = notifications.groupBy { n =>
      val date = new js.Date(n.date.toDouble)
      if (date.getTime() >= todayStart) "今日"
      else if (date.getTime() >= yesterdayStart) "昨日"
      else "それ以前"
    }

    List("今日", "昨日", "それ以前").flatMap(key => grouped.get(key).map(key -> _))
  }
}
