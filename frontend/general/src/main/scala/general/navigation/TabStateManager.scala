package general.navigation

import com.raquo.laminar.api.L.*
import models.*

object TabStateManager {
  private val homeStack = Var[List[Page]](List(HomePage))
  private val postStack = Var[List[Page]](List(PostPage))
  private val myPageStack = Var[List[Page]](List(MyPage))

  val activeTabId = Var[String]("home")

  // popPage実行中かどうかを管理（Routerのフックによる重複追加を防ぐ）
  private var isPopping = false

  // 現在のアクティブなスタックを取得するヘルパー
  def currentStackSignal: Signal[List[Page]] = activeTabId.signal.flatMapSwitch {
    case "post" => postStack.signal
    case "my"   => myPageStack.signal
    case _      => homeStack.signal
  }

  def pushPage(page: Page): Unit = {
    if (isPopping) return

    val tab = getTargetTab(page)
    activeTabId.set(tab)
    val stackVar = getStackVar(tab)

    val currentStack = stackVar.now()

    // 修正箇所：同じページ（IDが同じWordDetailなど）がトップにある場合
    currentStack.headOption match {
      case Some(topPage) if topPage == page =>
        return // 完全に一致なら何もしない
      case Some(topPage: WordDetailPage) if page.isInstanceOf[WordDetailPage] =>
        // IDは同じだがタブが違うWordDetailPageの場合、スタックの先頭を差し替える（履歴を深くしすぎないため）
        stackVar.set(page :: currentStack.tail)
      case _ =>
        // それ以外は新規追加
        stackVar.update(current => page :: current)
    }
  }

  def silentUpdate(page: Page): Unit = {
    val tab = getTargetTab(page)
    val stackVar = getStackVar(tab)
    val currentStack = stackVar.now()

    if (currentStack.nonEmpty) {
      // 先頭（今見ているWordDetailPage）を、新しいタブ情報を持ったインスタンスに差し替える
      stackVar.set(page :: currentStack.tail)
    }
  }

  def popPage(): Unit = {
    val tab = activeTabId.now()
    val stackVar = getStackVar(tab)
    val currentStack = stackVar.now()

    if (currentStack.size > 1) {
      isPopping = true // フラグON
      val nextStack = currentStack.tail
      stackVar.set(nextStack)

      // Routerで遷移。この直後にRouterのforeachが走るが、isPoppingなので無視される
      Router.router.pushState(nextStack.head)

      // 処理が終わったらフラグを戻す
      isPopping = false
    }
  }

  private def getTargetTab(page: Page): String = page match {
    case PostPage            => "post"
    case p if isMyPageTab(p) => "my"
    case _                   => "home"
  }

  private def isMyPageTab(page: Page): Boolean = page match {
    case MyPage | MyPostsPage | FavoritePostsPage | ReactedPostsPage | FollowedTagsPage | FollowedWordsPage |
        HistoryPage =>
      true
    case _ => false
  }

  private def getStackVar(tabId: String): Var[List[Page]] = tabId match {
    case "post" => postStack
    case "my"   => myPageStack
    case _      => homeStack
  }

  def currentStackSignalFor(tabId: String): Var[List[Page]] = tabId match {
    case "post" => postStack
    case "my"   => myPageStack
    case _      => homeStack
  }

  // 二度押し時にスタックをルート以外削除して遷移する
  def resetStack(rootPage: Page): Unit = {
    val stackVar = getStackVar(getTargetTab(rootPage))
    stackVar.set(List(rootPage))
    Router.navigateTo(rootPage)
  }

  // 特定のスレッド詳細ページをすべてのスタックから除去する
  def removeThreadPage(id: Long): Unit = {
    val filterFn = (stack: List[Page]) =>
      stack.filter {
        case p: ThreadDetailPage if p.threadId == id => false
        case _                                       => true
      }

    homeStack.update(filterFn)
    postStack.update(filterFn)
    myPageStack.update(filterFn)
  }
}
