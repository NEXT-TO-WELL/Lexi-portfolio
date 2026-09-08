package models

import upickle.default._

sealed trait Page

case object HomePage extends Page
case object PostPage extends Page
case object MyPage extends Page
// 検索ワードから検索結果画面を表示する
case class SearchPage(
    query: String = "", // 自由入力、または表示用文字列
    wordId: Option[Long] = None, // 確定したワードID
    tagId: Option[Long] = None // 確定したタグID
) extends Page
// wordId と tagId を使って特定のワード詳細を表示する
sealed trait WordDetailTab
object WordDetailTab {
  case object Meaning extends WordDetailTab
  case object Example extends WordDetailTab
  case object Thread extends WordDetailTab

  import upickle.default._
  implicit val rw: ReadWriter[WordDetailTab] = readwriter[String].bimap[WordDetailTab](
    {
      case Meaning => "meaning"
      case Example => "example"
      case Thread  => "thread"
    },
    {
      case "example" => Example
      case "thread"  => Thread
      case _         => Meaning
    }
  )
}
case class WordDetailPage(wordId: Long, tagId: Long, initialTab: WordDetailTab = WordDetailTab.Meaning) extends Page
case class ThreadDetailPage(threadId: Long) extends Page

case object MyPostsPage extends Page
case object FavoritePostsPage extends Page
case object ReactedPostsPage extends Page
case object FollowedTagsPage extends Page // フォロー中のタグ一覧用
case object FollowedWordsPage extends Page // フォロー中の言葉一覧用
case object HistoryPage extends Page

// 各 Page に ReadWriter を持たせます
object Page {
  implicit val rw: ReadWriter[Page] = ReadWriter.merge(
    macroRW[HomePage.type],
    macroRW[PostPage.type],
    macroRW[MyPage.type],
    macroRW[SearchPage],
    macroRW[WordDetailPage],
    macroRW[ThreadDetailPage],
    macroRW[MyPostsPage.type],
    macroRW[FavoritePostsPage.type],
    macroRW[ReactedPostsPage.type],
    macroRW[FollowedTagsPage.type],
    macroRW[FollowedWordsPage.type],
    macroRW[HistoryPage.type]
  )
}
