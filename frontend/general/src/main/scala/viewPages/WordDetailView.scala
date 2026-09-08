package viewPages

import general.auth.AuthStorage
import general.ui.{DraftStorage, PullToRefresh}

import scala.scalajs.js
import com.raquo.laminar.api.L.*
import general.navigation.{Router, TabStateManager}
import general.word.WordDetailFilterControls
import general.word.WordDetailFilters.*
import general.word.WordDetailApi
import general.word.{WordDetailHeader, WordDetailPostModal, WordDetailState}
import general.word.WordDetailLists
import general.word.WordDetailLists.{ExampleActions, MeaningActions, Pagination, ThreadActions}
import models.*
import shared.api.{ContentQuoteReference, ContentQuoteTypes, ContentQuoteViewValue, ExampleViewValue, MeaningViewValue}
import general.word.DetailPostForm
import component.{Modal, ModalType}
import general.ui.EditModal
import general.ui.Toast
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global

object WordDetailView {
  private val state = new WordDetailState
  import state.*

  def apply(page: WordDetailPage): HtmlElement = {
    val wordId = page.wordId
    val tagId = page.tagId
    val currentUserId: Option[String] = AuthStorage.getUserId
    val isGuest = AuthStorage.getToken.isEmpty

    state.prepare(wordId, tagId, page.initialTab)
    val threadDraftContext = s"word-$wordId-tag-$tagId"
    if (selectedThreadQuoteVar.now().isEmpty) {
      selectedThreadQuoteVar.set(DraftStorage.loadQuote("thread", threadDraftContext))
    }

    div(
      cls := "w-full h-full min-h-0 flex flex-col bg-white dark:bg-gray-900 overflow-hidden",
      div(cls := "flex-none h-14"), // Headerスペーサー

      div(
        cls := "flex-none p-4 pb-0 space-y-2", // space-y-4 から 2 に変更
        child <-- headerVar.signal.map {
          case None         => div(cls := "text-center py-4 text-gray-400", "読み込み中...")
          case Some(header) =>
            WordDetailHeader(
              header,
              isGuest,
              isFollowedVar.signal,
              copyWord = copyWord,
              toggleFollow = () => toggleFollow(wordId, tagId)
            )
        },
        WordDetailFilterControls(
          activeTab.signal,
          showJokesVar,
          sortTypeVar,
          questionFilterVar,
          threadTypeFilterVar
        ),
        div(
          cls := "flex border-b border-gray-200 dark:border-gray-800",
          tabButton("語釈", WordDetailTab.Meaning, wordId, tagId),
          tabButton("用例", WordDetailTab.Example, wordId, tagId),
          tabButton("スレッド", WordDetailTab.Thread, wordId, tagId)
        )
      ),

      PullToRefresh(() => refreshCurrentTab(wordId, tagId))(
        div(
          cls := "p-4 pb-32",
          child <-- activeTab.signal.flatMapSwitch {
            case WordDetailTab.Meaning =>
              Signal.combine(meaningsVar.signal, showJokesVar.signal, sortTypeVar.signal).map {
                (items: Seq[MeaningViewValue], showJokes: Boolean, sortType: SortType) =>
                  val filtered = if (showJokes) items else items.filterNot(_.meaning.isJoke)
                  val sorted = sortMeanings(filtered, sortType)
                  WordDetailLists.meanings(
                    sorted,
                    currentUserId,
                    isGuest,
                    MeaningActions(
                      showPostForm = () => isShowingMeaningModal.set(true),
                      favoriteChanged = updateMeaningFavorite,
                      delete = deleteMeaning,
                      edit = updateMeaning,
                      reactionChanged = updateMeaningReaction,
                      quote = item => selectMeaningQuote(item, wordId, tagId)
                    ),
                    Pagination(
                      meaningsHasMoreVar.signal,
                      meaningsLoadingVar.signal,
                      () => meaningsLoadingVar.now(),
                      () => fetchMoreMeanings(wordId, tagId)
                    )
                  )
              }
            case WordDetailTab.Example =>
              Signal.combine(examplesVar.signal, showJokesVar.signal, sortTypeVar.signal).map {
                (items: Seq[ExampleViewValue], showJokes: Boolean, sortType: SortType) =>
                  val filtered = if (showJokes) items else items.filterNot(_.example.isJoke)
                  val sorted = sortExamples(filtered, sortType)
                  WordDetailLists.examples(
                    sorted,
                    currentUserId,
                    isGuest,
                    ExampleActions(
                      showPostForm = () => isShowingExampleModal.set(true),
                      favoriteChanged = updateExampleFavorite,
                      delete = deleteExample,
                      edit = updateExample,
                      reactionChanged = updateExampleReaction,
                      quote = item => selectExampleQuote(item, wordId, tagId)
                    ),
                    Pagination(
                      examplesHasMoreVar.signal,
                      examplesLoadingVar.signal,
                      () => examplesLoadingVar.now(),
                      () => fetchMoreExamples(wordId, tagId)
                    )
                  )
              }
            case WordDetailTab.Thread =>
              val filteredSignal = Signal
                .combine(threadsVar.signal, sortTypeVar.signal, questionFilterVar.signal, threadTypeFilterVar.signal)
                .map {
                  (
                      items: Seq[ThreadViewValue],
                      sortType: SortType,
                      questionFilter: QuestionFilter,
                      typeFilter: ThreadTypeFilter
                  ) =>
                    val filteredByQuestion = filterThreads(items, questionFilter)
                    val filteredByType = filterThreadsByType(filteredByQuestion, typeFilter)
                    sortThreads(filteredByType, sortType)
                }
              Signal.fromValue(
                WordDetailLists.threads(
                  wordId,
                  tagId,
                  filteredSignal,
                  isGuest,
                  isShowingThreadForm.signal,
                  selectedThreadQuoteVar,
                  ThreadActions(
                    showPostForm = () => isShowingThreadForm.set(true),
                    closePostForm = () => isShowingThreadForm.set(false),
                    post = (title, typeId, quote, onSuccess) =>
                      postNewThread(wordId, tagId, title, typeId, quote, onSuccess),
                    clearQuote = () => {
                      selectedThreadQuoteVar.set(None)
                      DraftStorage.saveQuote("thread", threadDraftContext, None)
                    }
                  ),
                  Pagination(
                    threadsHasMoreVar.signal,
                    threadsLoadingVar.signal,
                    () => threadsLoadingVar.now(),
                    () => fetchMoreThreads(wordId, tagId)
                  )
                )
              )
          }
        ),
        div(cls := "h-16 flex-none")
      ),

      child <-- isShowingMeaningModal.signal.map(
        if (_)
          WordDetailPostModal(
            "語釈を投稿",
            DetailPostForm.Meaning,
            s"word-$wordId-tag-$tagId",
            (p, onSuccess) => postMeaning(wordId, tagId, p, onSuccess),
            () => isShowingMeaningModal.set(false)
          )
        else emptyNode
      ),
      child <-- isShowingExampleModal.signal.map(
        if (_)
          WordDetailPostModal(
            "用例を投稿",
            DetailPostForm.Example,
            s"word-$wordId-tag-$tagId",
            (p, onSuccess) => postExample(wordId, tagId, p, onSuccess),
            () => isShowingExampleModal.set(false)
          )
        else emptyNode
      ),

      onMountCallback { ctx =>
        js.timers.setTimeout(0) {
          val scrollContainer = ctx.thisNode.ref.querySelector(".overflow-y-auto")
          if (scrollContainer != null) scrollContainer.scrollTop = 0
        }

        if (!state.headerLoaded) fetchHeader(wordId, tagId)

        page.initialTab match {
          case WordDetailTab.Meaning => if (!state.meaningsLoaded) fetchMeanings(wordId, tagId)
          case WordDetailTab.Example => if (!state.examplesLoaded) fetchExamples(wordId, tagId)
          case WordDetailTab.Thread  => if (!state.threadsLoaded) fetchThreads(wordId, tagId)
        }
      }
    )
  }

  private def copyWord(word: String): Unit =
    dom.window.navigator.clipboard.writeText(word).toFuture.foreach { _ =>
      Toast.show(s"「$word」をコピーしました")
    }

  // --- API 処理 ---
  private def fetchHeader(wordId: Long, tagId: Long): Unit = {
    WordDetailApi.fetchHeader(wordId, tagId).foreach { h =>
      headerVar.set(Some(h))
      isFollowedVar.set(h.isFollowed)
      state.markHeaderLoaded()
    }
  }

  private def refreshCurrentTab(wordId: Long, tagId: Long): Unit = {
    fetchHeader(wordId, tagId)
    activeTab.now() match {
      case WordDetailTab.Meaning => fetchMeanings(wordId, tagId)
      case WordDetailTab.Example => fetchExamples(wordId, tagId)
      case WordDetailTab.Thread  => fetchThreads(wordId, tagId)
    }
  }

  private def toggleFollow(wordId: Long, tagId: Long): Unit = {
    if (AuthStorage.getToken.isEmpty) {
      Modal.show("ログインが必要です", "フォロー機能を利用するにはログインしてください。", ModalType.Info)
      return
    }
    WordDetailApi.toggleFollow(wordId, tagId).foreach(isFollowedVar.set)
  }

  private def fetchMeanings(wordId: Long, tagId: Long): Unit = {
    meaningsLoadingVar.set(true)
    meaningsHasMoreVar.set(true)
    val request =
      if (dom.window.location.hash.startsWith("#meaning-")) WordDetailApi.fetchAllMeanings(wordId, tagId)
      else WordDetailApi.fetchMeanings(wordId, tagId)
    request.foreach { data =>
      meaningsVar.set(data)
      meaningsLoadingVar.set(false)
      state.markMeaningsLoaded()
      if (data.length < WordDetailApi.PageSize) meaningsHasMoreVar.set(false)
      scrollToQuotedSource()
    }
  }

  private def fetchMoreMeanings(wordId: Long, tagId: Long): Unit = {
    if (meaningsLoadingVar.now() || !meaningsHasMoreVar.now()) return

    val lastId = meaningsVar.now().lastOption.flatMap(_.meaning.meaningId)
    lastId.foreach { id =>
      meaningsLoadingVar.set(true)
      WordDetailApi.fetchMeanings(wordId, tagId, Some(id)).foreach { newData =>
        meaningsVar.update(_ ++ newData)
        meaningsLoadingVar.set(false)
        if (newData.length < WordDetailApi.PageSize) meaningsHasMoreVar.set(false)
      }
    }
  }

  private def fetchExamples(wordId: Long, tagId: Long): Unit = {
    examplesLoadingVar.set(true)
    examplesHasMoreVar.set(true)
    val request =
      if (dom.window.location.hash.startsWith("#example-")) WordDetailApi.fetchAllExamples(wordId, tagId)
      else WordDetailApi.fetchExamples(wordId, tagId)
    request.foreach { data =>
      examplesVar.set(data)
      examplesLoadingVar.set(false)
      state.markExamplesLoaded()
      if (data.length < WordDetailApi.PageSize) examplesHasMoreVar.set(false)
      scrollToQuotedSource()
    }
  }

  private def scrollToQuotedSource(): Unit =
    Option(dom.window.location.hash).filter(_.length > 1).foreach { hash =>
      js.timers.setTimeout(0) {
        Option(dom.document.getElementById(hash.drop(1))).foreach(_.scrollIntoView())
      }
    }

  private def fetchMoreExamples(wordId: Long, tagId: Long): Unit = {
    if (examplesLoadingVar.now() || !examplesHasMoreVar.now()) return

    val lastId = examplesVar.now().lastOption.flatMap(_.example.exampleId)
    lastId.foreach { id =>
      examplesLoadingVar.set(true)
      WordDetailApi.fetchExamples(wordId, tagId, Some(id)).foreach { newData =>
        examplesVar.update(_ ++ newData)
        examplesLoadingVar.set(false)
        if (newData.length < WordDetailApi.PageSize) examplesHasMoreVar.set(false)
      }
    }
  }

  private def fetchThreads(wordId: Long, tagId: Long): Unit = {
    threadsLoadingVar.set(true)
    threadsHasMoreVar.set(true)
    WordDetailApi.fetchThreads(wordId, tagId).foreach { data =>
      threadsVar.set(data)
      threadsLoadingVar.set(false)
      state.markThreadsLoaded()
      if (data.length < WordDetailApi.PageSize) threadsHasMoreVar.set(false)
    }
  }

  private def fetchMoreThreads(wordId: Long, tagId: Long): Unit = {
    if (threadsLoadingVar.now() || !threadsHasMoreVar.now()) return

    val lastId = threadsVar.now().lastOption.map(_.threadId)
    lastId.foreach { id =>
      threadsLoadingVar.set(true)
      WordDetailApi.fetchThreads(wordId, tagId, Some(id)).foreach { newData =>
        threadsVar.update(_ ++ newData)
        threadsLoadingVar.set(false)
        if (newData.length < WordDetailApi.PageSize) threadsHasMoreVar.set(false)
      }
    }
  }

  // --- 投稿系 ---
  private def postMeaning(
      wordId: Long,
      tagId: Long,
      payload: DetailPostForm.PostPayload,
      onSuccess: () => Unit
  ): Unit = {
    if (AuthStorage.getToken.isEmpty) { dom.window.location.href = "/signIn"; return }
    val trimmedText = util.Validation.trimAll(payload.text)
    if (!util.Validation.notBlank(payload.text)) {
      component.Modal.show("エラー", "語釈に空白のみは使用できません", component.ModalType.Info, {})
      return
    }
    val req = MeaningPostRequest(wordId, tagId, trimmedText, payload.isJoke)
    WordDetailApi.createMeaning(req).foreach { res =>
      activeTab.set(WordDetailTab.Meaning)
      dom.window.history.replaceState(js.undefined, "", s"/word/$wordId/tag/$tagId/meaning")
      TabStateManager.silentUpdate(WordDetailPage(wordId, tagId, WordDetailTab.Meaning))
      meaningsVar.update(res +: _)
      isShowingMeaningModal.set(false)
      onSuccess()
      Toast.show("語釈を投稿しました")
    }
  }

  private def postExample(
      wordId: Long,
      tagId: Long,
      payload: DetailPostForm.PostPayload,
      onSuccess: () => Unit
  ): Unit = {
    if (AuthStorage.getToken.isEmpty) { dom.window.location.href = "/signIn"; return }
    val trimmedText = util.Validation.trimAll(payload.text)
    if (!util.Validation.notBlank(payload.text)) {
      component.Modal.show("エラー", "用例に空白のみは使用できません", component.ModalType.Info, {})
      return
    }
    val req = ExamplePostRequest(wordId, tagId, trimmedText, payload.isJoke)
    WordDetailApi.createExample(req).foreach { res =>
      activeTab.set(WordDetailTab.Example)
      dom.window.history.replaceState(js.undefined, "", s"/word/$wordId/tag/$tagId/example")
      TabStateManager.silentUpdate(WordDetailPage(wordId, tagId, WordDetailTab.Example))
      examplesVar.update(res +: _)
      isShowingExampleModal.set(false)
      onSuccess()
      Toast.show("用例を投稿しました")
    }
  }

  private def postNewThread(
      wordId: Long,
      tagId: Long,
      title: String,
      typeId: String,
      quote: Option[ContentQuoteViewValue],
      onSuccess: () => Unit
  ): Unit = {
    if (AuthStorage.getToken.isEmpty) { dom.window.location.href = "/signIn"; return }
    val trimmedTitle = util.Validation.trimAll(title)
    if (!util.Validation.notBlank(title)) {
      component.Modal.show("エラー", "タイトルに空白のみは使用できません", component.ModalType.Info, {})
      return
    }
    val quoteReference = quote.map(value => ContentQuoteReference(value.sourceContentTypeId, value.sourceContentId))
    WordDetailApi.createThread(ThreadPostRequest(wordId, tagId, typeId, trimmedTitle, quoteReference)).foreach { res =>
      threadsVar.update(res +: _)
      isShowingThreadForm.set(false)
      selectedThreadQuoteVar.set(None)
      DraftStorage.saveQuote("thread", s"word-$wordId-tag-$tagId", None)
      onSuccess()
      Toast.show("スレッドを作成しました")
      Router.router.pushState(ThreadDetailPage(res.threadId))
    }
  }

  private def selectMeaningQuote(item: MeaningViewValue, wordId: Long, tagId: Long): Unit =
    item.meaning.meaningId.foreach { contentId =>
      selectThreadQuote(
        ContentQuoteViewValue(
          ContentQuoteTypes.Meaning,
          contentId,
          false,
          Some(item.meaning.meaningText),
          Some(item.userName),
          item.displayId,
          Some(wordId),
          Some(tagId),
          headerVar.now().map(_.wordMain),
          headerVar.now().map(_.tagName)
        ),
        wordId,
        tagId
      )
    }

  private def selectExampleQuote(item: ExampleViewValue, wordId: Long, tagId: Long): Unit =
    item.example.exampleId.foreach { contentId =>
      selectThreadQuote(
        ContentQuoteViewValue(
          ContentQuoteTypes.Example,
          contentId,
          false,
          Some(item.example.exampleText),
          Some(item.userName),
          item.displayId,
          Some(wordId),
          Some(tagId),
          headerVar.now().map(_.wordMain),
          headerVar.now().map(_.tagName)
        ),
        wordId,
        tagId
      )
    }

  private def selectThreadQuote(quote: ContentQuoteViewValue, wordId: Long, tagId: Long): Unit = {
    selectedThreadQuoteVar.set(Some(quote))
    DraftStorage.saveQuote("thread", s"word-$wordId-tag-$tagId", Some(quote))
    isShowingThreadForm.set(true)
    activeTab.set(WordDetailTab.Thread)
    dom.window.history.replaceState(js.undefined, "", s"/word/$wordId/tag/$tagId/thread")
    TabStateManager.silentUpdate(WordDetailPage(wordId, tagId, WordDetailTab.Thread))
    if (!state.threadsLoaded) fetchThreads(wordId, tagId)
  }

  // --- 削除関数 ---
  private def deleteMeaning(meaningId: Long): Unit = {
    WordDetailApi.deleteMeaning(meaningId).foreach { _ =>
      meaningsVar.update(_.filterNot(_.meaning.meaningId.contains(meaningId)))
    }
  }

  private def deleteExample(exampleId: Long): Unit = {
    WordDetailApi.deleteExample(exampleId).foreach { _ =>
      examplesVar.update(_.filterNot(_.example.exampleId.contains(exampleId)))
    }
  }

  // --- 編集関数 ---
  private def updateMeaning(meaningId: Long, newText: String, isJoke: Boolean): Unit = {
    val trimmedText = util.Validation.trimAll(newText)
    if (!util.Validation.notBlank(newText)) {
      EditModal.isSubmitting.set(false)
      component.Modal.show("エラー", "語釈に空白のみは使用できません", component.ModalType.Info, {})
      return
    }
    val requestBody = models.MeaningPostRequest(meaningText = trimmedText, wordId = 0L, tagId = 0L, isJoke = isJoke)

    util.ErrorHandler.handle(
      WordDetailApi.updateMeaning(meaningId, requestBody),
      onSuccess = (updatedView: shared.api.MeaningViewValue) => {
        meaningsVar.update(_.map { item =>
          if (item.meaning.meaningId.contains(meaningId)) {
            updatedView
          } else {
            item
          }
        })
      },
      showSuccessModal = true,
      successMessage = "語釈を更新しました"
    )
  }

  private def updateExample(exampleId: Long, newText: String, isJoke: Boolean): Unit = {
    val trimmedText = util.Validation.trimAll(newText)
    if (!util.Validation.notBlank(newText)) {
      EditModal.isSubmitting.set(false)
      component.Modal.show("エラー", "用例に空白のみは使用できません", component.ModalType.Info, {})
      return
    }
    val requestBody = models.ExamplePostRequest(exampleText = trimmedText, wordId = 0L, tagId = 0L, isJoke = isJoke)

    util.ErrorHandler.handle(
      WordDetailApi.updateExample(exampleId, requestBody),
      onSuccess = (updatedView: shared.api.ExampleViewValue) => {
        examplesVar.update(_.map { item =>
          if (item.example.exampleId.contains(exampleId)) {
            updatedView
          } else {
            item
          }
        })
      },
      showSuccessModal = true,
      successMessage = "用例を更新しました"
    )
  }

  private def updateMeaningFavorite(meaningId: Long, isFavorite: Boolean): Unit =
    meaningsVar.update(_.map { item =>
      if (item.meaning.meaningId.contains(meaningId)) item.copy(isFavorite = isFavorite)
      else item
    })

  private def updateExampleFavorite(exampleId: Long, isFavorite: Boolean): Unit =
    examplesVar.update(_.map { item =>
      if (item.example.exampleId.contains(exampleId)) item.copy(isFavorite = isFavorite)
      else item
    })

  private def updateMeaningReaction(
      meaningId: Long,
      reactionType: String,
      active: Boolean,
      count: Int
  ): Unit =
    meaningsVar.update(_.map { item =>
      if (!item.meaning.meaningId.contains(meaningId)) item
      else
        reactionType match {
          case "good"    => item.copy(isGood = active, meaning = item.meaning.copy(meaningGoodCount = count))
          case "helpful" => item.copy(isHelpful = active, meaning = item.meaning.copy(meaningHelpfulCount = count))
          case "funny"   => item.copy(isFunny = active, meaning = item.meaning.copy(meaningFunnyCount = count))
          case _         => item
        }
    })

  private def updateExampleReaction(
      exampleId: Long,
      reactionType: String,
      active: Boolean,
      count: Int
  ): Unit =
    examplesVar.update(_.map { item =>
      if (!item.example.exampleId.contains(exampleId)) item
      else
        reactionType match {
          case "good"    => item.copy(isGood = active, example = item.example.copy(exampleGoodCount = count))
          case "helpful" => item.copy(isHelpful = active, example = item.example.copy(exampleHelpfulCount = count))
          case "funny"   => item.copy(isFunny = active, example = item.example.copy(exampleFunnyCount = count))
          case _         => item
        }
    })

  private def tabButton(title: String, tab: WordDetailTab, wordId: Long, tagId: Long): HtmlElement =
    button(
      cls <-- activeTab.signal.map(a =>
        s"flex-1 py-3 text-sm font-bold border-b-2 transition ${
            if (a == tab) "border-blue-500 text-blue-600 dark:text-blue-400"
            else "border-transparent text-gray-400 dark:text-gray-500"
          }"
      ),
      title,
      onClick --> { _ =>
        if (activeTab.now() != tab) {
          val newPage = WordDetailPage(wordId, tagId, tab)

          // 1. URLを Waypoint（Router）を介さず直接書き換える
          // これにより、コンポーネントの再マウント（読み込み）が発生しない
          val tabPath = tab.toString.toLowerCase
          dom.window.history.replaceState(js.undefined, "", s"/word/$wordId/tag/$tagId/$tabPath")

          // 2. TabStateManager のスタックの中身を静かに更新する
          // これで、スレッド詳細から戻った時の「戻り先ページ」が更新される
          TabStateManager.silentUpdate(newPage)

          // 3. UIの状態を切り替える
          activeTab.set(tab)

          tab match {
            case WordDetailTab.Meaning => if (!state.meaningsLoaded) fetchMeanings(wordId, tagId)
            case WordDetailTab.Example => if (!state.examplesLoaded) fetchExamples(wordId, tagId)
            case WordDetailTab.Thread  => if (!state.threadsLoaded) fetchThreads(wordId, tagId)
          }
        }
      }
    )

}
