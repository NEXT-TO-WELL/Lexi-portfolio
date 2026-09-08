package general.ui

import com.raquo.laminar.api.L.*
import org.scalajs.dom

object PullToRefresh {
  private val TriggerDistance = 76.0
  private val MaxPullDistance = 112.0

  object DesktopRefresh {
    val actionVar = Var(Option.empty[() => Unit])
    val refreshingVar = Var(false)
    private var nextRegistrationId = 0L
    private var activeRegistrationId = Option.empty[Long]

    def register(action: () => Unit): Long = {
      nextRegistrationId += 1
      activeRegistrationId = Some(nextRegistrationId)
      actionVar.set(Some(action))
      nextRegistrationId
    }

    def unregister(registrationId: Long): Unit = {
      if (activeRegistrationId.contains(registrationId)) {
        activeRegistrationId = None
        actionVar.set(None)
      }
    }

    def run(): Unit = {
      actionVar.now().foreach { action =>
        if (!refreshingVar.now()) action()
      }
    }
  }

  def apply(
      onRefresh: () => Unit,
      containerCls: String = "flex-grow overflow-y-auto min-h-0",
      contentCls: String = "",
      label: String = "更新"
  )(content: Modifier[HtmlElement]*): HtmlElement = {
    val pullDistance = Var(0.0)
    val refreshing = Var(false)
    var startY = 0.0
    var pulling = false

    def resetPull(): Unit = {
      pulling = false
      startY = 0.0
      pullDistance.set(0.0)
    }

    def runRefresh(): Unit = {
      if (!refreshing.now()) {
        refreshing.set(true)
        DesktopRefresh.refreshingVar.set(true)
        try onRefresh()
        finally
          dom.window.setTimeout(
            () => {
              refreshing.set(false)
              DesktopRefresh.refreshingVar.set(false)
              resetPull()
            },
            700
          )
      }
    }

    def beginPull(clientY: Double, element: dom.html.Element): Unit = {
      if (!refreshing.now() && element.scrollTop <= 0) {
        pulling = true
        startY = clientY
      }
    }

    def updatePull(clientY: Double, element: dom.html.Element): Unit = {
      if (pulling && !refreshing.now()) {
        val distance = clientY - startY
        if (element.scrollTop > 0 || distance <= 0) resetPull()
        else pullDistance.set(Math.min(distance * 0.55, MaxPullDistance))
      }
    }

    def finishPull(): Unit = {
      if (pulling && pullDistance.now() >= TriggerDistance && !refreshing.now()) {
        pullDistance.set(TriggerDistance)
        runRefresh()
      } else resetPull()
    }

    val desktopAction = () => runRefresh()
    val desktopRegistrationId = DesktopRefresh.register(desktopAction)
    val pullStateSignal = refreshing.signal.combineWith(pullDistance.signal).map { case (isRefreshing, distance) =>
      if (isRefreshing) "refreshing"
      else if (distance >= TriggerDistance) "ready"
      else if (distance > 4) "pulling"
      else "idle"
    }

    div(
      dataAttr("pull-to-refresh") := "true",
      cls := s"$containerCls overscroll-contain touch-pan-y md:overflow-visible md:overscroll-auto",
      onUnmountCallback { _ =>
        DesktopRefresh.unregister(desktopRegistrationId)
      },
      onPointerDown --> { ev =>
        if (ev.pointerType != "touch")
          beginPull(ev.clientY, ev.currentTarget.asInstanceOf[dom.html.Element])
      },
      onPointerMove --> { ev =>
        if (ev.pointerType != "touch")
          updatePull(ev.clientY, ev.currentTarget.asInstanceOf[dom.html.Element])
      },
      onPointerUp --> { ev =>
        if (ev.pointerType != "touch") finishPull()
      },
      onPointerCancel --> { ev =>
        if (ev.pointerType != "touch") resetPull()
      },
      onTouchStart --> { ev =>
        if (ev.touches.length == 1)
          beginPull(ev.touches.item(0).clientY, ev.currentTarget.asInstanceOf[dom.html.Element])
        else resetPull()
      },
      onTouchMove --> { ev =>
        if (ev.touches.length == 1)
          updatePull(ev.touches.item(0).clientY, ev.currentTarget.asInstanceOf[dom.html.Element])
        else resetPull()
      },
      onTouchEnd --> { _ => finishPull() },
      onTouchCancel --> { _ => resetPull() },
      div(
        dataAttr("pull-state") <-- pullStateSignal,
        aria.live := "polite",
        cls := "pointer-events-none sticky top-14 z-20 -mb-12 flex h-12 items-center justify-center text-xs font-bold text-blue-600 dark:text-blue-300 transition-opacity",
        opacity <-- pullDistance.signal.map(d => if (d > 4) "1" else "0"),
        transform <-- pullDistance.signal.map(d => s"translateY(${Math.max(0.0, d - 48.0)}px)"),
        child <-- refreshing.signal.combineWith(pullDistance.signal).map { case (isRefreshing, distance) =>
          val text = if (isRefreshing) "更新中..." else if (distance >= TriggerDistance) "離して更新" else s"下に引いて$label"
          div(
            cls := "rounded-full border border-blue-100 dark:border-blue-800 bg-white/95 dark:bg-gray-900/95 px-4 py-2 shadow-sm",
            text
          )
        }
      ),
      div(cls := contentCls, content)
    )
  }
}
