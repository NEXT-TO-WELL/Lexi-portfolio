package viewPages

import general.auth.AuthStorage
import general.ui.GuestDisplay
import general.ui.PullToRefresh

import com.raquo.laminar.api.L.*
import general.mypage.{MypageApi, MypageProfileContent, MypageSettings, MypageState}
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

object MypageView {
  private val state = new MypageState
  import state.*

  def apply(): HtmlElement = {
    PullToRefresh(
      onRefresh = () => if (AuthStorage.getToken.isDefined) fetchProfile(),
      containerCls = "w-full h-full min-h-0 overflow-y-auto overscroll-contain md:h-auto md:overflow-visible",
      contentCls = "w-full mx-auto bg-gray-50 dark:bg-gray-950 md:px-4"
    )(
      onMountCallback { _ =>
        if (AuthStorage.getToken.isDefined) {
          fetchProfile()
        }
      },
      child <-- profileVar.signal.map {
        case None if AuthStorage.getToken.isDefined =>
          div(cls := "text-center py-20 text-gray-400 dark:text-gray-500", "プロファイルを読み込み中...")
        case Some(profile) if AuthStorage.getToken.isDefined =>
          MypageProfileContent(
            profile,
            MypageProfileContent.SettingsActions(
              editName = name => MypageSettings.showNameEditor(name, () => fetchProfile()),
              showPassword = () => showPasswordModalVar.set(true),
              showDeleteAccount = () => showDeleteModalVar.set(true)
            )
          )
        case _ =>
          GuestDisplay("ログインすると全ての機能が利用できます。")
      },
      child <-- showPasswordModalVar.signal.map(
        if (_) MypageSettings.passwordModal(showPasswordModalVar) else emptyNode
      ),
      child <-- showDeleteModalVar.signal.map(
        if (_) MypageSettings.deleteAccountModal(showDeleteModalVar) else emptyNode
      )
    )
  }

  private def fetchProfile(): Unit = {
    if (isFetchingVar.now()) return
    isFetchingVar.set(true)

    MypageApi.fetchProfile().onComplete {
      case scala.util.Success(res) =>
        profileVar.set(Some(res))
        isFetchingVar.set(false)
      case scala.util.Failure(e) =>
        dom.console.error(s"Profile fetch failed: ${e.getMessage}")
        isFetchingVar.set(false)
    }
  }

  def refreshProfile(): Unit = {
    fetchProfile()
  }
}
