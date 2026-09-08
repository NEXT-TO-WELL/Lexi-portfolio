package viewPages
import general.ui.PullToRefresh
import component.{Modal, ModalType}

import com.raquo.laminar.api.L.*
import general.mypage.{MypageSavedPostFilters, MypageSavedPostsApi}
import general.mypage.MyPageWordItem
import models.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

object FavoritePostsView {
  sealed trait FavTab
  case object MeaningTab extends FavTab
  case object ExampleTab extends FavTab

  private val activeTab = Var[FavTab](MeaningTab)
  private val favMeanings = Var(Seq.empty[MyPageMeaningViewValue])
  private val favExamples = Var(Seq.empty[MyPageExampleViewValue])
  private val isFetching = Var(false)
  private val searchQuery = Var("")
  private var favMeaningsLoaded = false
  private var favExamplesLoaded = false

  def apply(): HtmlElement = {
    div(
      cls := "w-full h-full min-h-0 flex flex-col bg-gray-50 dark:bg-gray-950 overflow-hidden",
      div(cls := "flex-none h-14"), // ヘッダースペーサー

      // 固定ヘッダー & タブ
      div(
        cls := "flex-none bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 z-20 shadow-sm",
        div(
          cls := "h-0.5 bg-blue-500 transition-all duration-300",
          width <-- isFetching.signal.map(if (_) "100%" else "0%"),
          opacity <-- isFetching.signal.map(if (_) "1" else "0")
        ),
        div(cls := "flex", renderTabButton("語釈", MeaningTab), renderTabButton("用例", ExampleTab)),
        // 検索バー
        div(
          cls := "px-4 py-3 border-t border-gray-100 dark:border-gray-800",
          MypageSavedPostFilters.searchBar(searchQuery, "お気に入りを検索...")
        )
      ),

      // コンテンツエリア
      PullToRefresh(() => refreshCurrentTab())(
        div(
          cls := "p-4 pb-6",
          // ★ 修正ポイント: 全ての Var を結合して、同期的に HTML を生成する
          child <-- activeTab.signal
            .combineWith(isFetching.signal, favMeanings.signal, favExamples.signal, searchQuery.signal)
            .map { case (tab, loading, meanings, examples, query) =>
              if (loading) {
                MypageSavedPostFilters.loading()
              } else {
                tab match {
                  case MeaningTab =>
                    val filtered = MypageSavedPostFilters.filterMeanings(meanings, query)
                    MypageSavedPostFilters.list(filtered, query, "お気に入りはまだありません")(m =>
                      MyPageWordItem.meaningCard(
                        m,
                        onFavoriteToggle = Some { isFav =>
                          if (!isFav) removeFromList(m.meaningView.meaning.meaningId.getOrElse(0L), isMeaning = true)
                        }
                      )
                    )
                  case ExampleTab =>
                    val filtered = MypageSavedPostFilters.filterExamples(examples, query)
                    MypageSavedPostFilters.list(filtered, query, "お気に入りはまだありません")(e =>
                      MyPageWordItem.exampleCard(
                        e,
                        onFavoriteToggle = Some { isFav =>
                          if (!isFav) removeFromList(e.exampleView.example.exampleId.getOrElse(0L), isMeaning = false)
                        }
                      )
                    )
                }
              }
            }
        )
      ),

      onMountCallback { _ =>
        if (!favMeaningsLoaded) fetchFavMeanings()
      }
    )
  }

  private def refreshCurrentTab(): Unit = {
    activeTab.now() match {
      case MeaningTab => fetchFavMeanings()
      case ExampleTab => fetchFavExamples()
    }
  }

  // --- 削除ロジック（API連動版） ---
  private def removeFromList(id: Long, isMeaning: Boolean): Unit = {
    val contentName = if (isMeaning) "語釈" else "用例"

    // カスタムモーダルを表示
    Modal.show(
      title = "お気に入り解除",
      message = s"この${contentName}をお気に入りから削除してもよろしいですか？",
      mType = ModalType.Confirm,
      action = {
        // --- 「はい」を押した時の処理 ---
        val contentTypeId = if (isMeaning) "1" else "2"

        // 1. サーバーへリクエスト
        MypageSavedPostsApi.toggleFavorite(contentTypeId, id).onComplete {
          case scala.util.Success(_) => dom.console.log("お気に入り解除成功")
          case scala.util.Failure(e) => dom.console.error(s"解除失敗: ${e.getMessage}")
        }

        // 2. 画面上のリストを更新
        if (isMeaning)
          favMeanings.update(_.filterNot(_.meaningView.meaning.meaningId.contains(id)))
        else
          favExamples.update(_.filterNot(_.exampleView.example.exampleId.contains(id)))
      }
    )
  }

  private def renderTabButton(label: String, tab: FavTab): HtmlElement = {
    button(
      cls <-- activeTab.signal.map(t =>
        s"flex-1 py-4 text-sm font-bold border-b-2 transition-all ${
            if (t == tab) "border-blue-600 text-blue-600 dark:text-blue-400"
            else "border-transparent text-gray-400 dark:text-gray-500"
          }"
      ),
      onClick --> { _ =>
        if (!isFetching.now()) {
          activeTab.set(tab)
          tab match {
            case MeaningTab => if (!favMeaningsLoaded) fetchFavMeanings()
            case ExampleTab => if (!favExamplesLoaded) fetchFavExamples()
          }
        }
      },
      label
    )
  }

  private def fetchFavMeanings(): Unit =
    performFetch(MypageSavedPostsApi.fetchFavoriteMeanings(), favMeanings.set, () => favMeaningsLoaded = true)
  private def fetchFavExamples(): Unit =
    performFetch(MypageSavedPostsApi.fetchFavoriteExamples(), favExamples.set, () => favExamplesLoaded = true)

  private def performFetch[A](
      request: scala.concurrent.Future[Seq[A]],
      update: Seq[A] => Unit,
      markLoaded: () => Unit
  ): Unit = {
    isFetching.set(true)
    request.onComplete { result =>
      isFetching.set(false)
      markLoaded()
      result match {
        case scala.util.Success(items) => update(items)
        case scala.util.Failure(e)     => update(Nil)
      }
    }
  }
}
