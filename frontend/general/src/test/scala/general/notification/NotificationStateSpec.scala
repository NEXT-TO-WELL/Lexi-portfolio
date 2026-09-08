package general.notification

import org.scalatest.funsuite.AnyFunSuite

class NotificationStateSpec extends AnyFunSuite {
  test("markAllReadLocally clears the unread notification state") {
    NotificationState.hasUnreadVar.set(true)
    NotificationState.unreadCountVar.set(3)

    NotificationState.markAllReadLocally()

    assert(!NotificationState.hasUnreadVar.now())
    assert(NotificationState.unreadCountVar.now() == 0)
  }
}
