package general.mypage

import com.raquo.laminar.api.L.Var
import models.*

enum MyPostsTab {
  case Words, Meanings, Examples, Threads, Comments
}

final class MyPostsState {
  val activeTabVar = Var[MyPostsTab](MyPostsTab.Words)
  val wordsVar = Var(Seq.empty[MyPageWordTagViewValue])
  val meaningsVar = Var(Seq.empty[MyPageMeaningViewValue])
  val examplesVar = Var(Seq.empty[MyPageExampleViewValue])
  val threadsVar = Var(Seq.empty[ThreadViewValue])
  val commentsVar = Var(Seq.empty[CommentViewValue])
  val isFetchingVar = Var(false)

  private var loadedTabs = Set.empty[MyPostsTab]

  def isLoaded(tab: MyPostsTab): Boolean = loadedTabs.contains(tab)
  def markLoaded(tab: MyPostsTab): Unit = loadedTabs += tab
}
