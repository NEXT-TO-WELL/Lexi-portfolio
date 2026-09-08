package viewPages

import general.auth.AuthStorage
import component.{Modal, ModalType}
import general.ui.{DraftStorage, GuestDisplay, SubmitButton, Toast}

import com.raquo.laminar.api.L.*
import general.navigation.Router
import models.*
import util.{ApiClient, Debouncer}
import general.ui.FormComponents.*
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

object PostView {
  // --- 状態管理 ---
  private val restoredDraft = DraftStorage.postDraft
  private val wordVar = Var(restoredDraft.word)
  private val readingVar = Var(restoredDraft.reading)
  private val tagVar = Var(restoredDraft.tag)
  private val meaningVar = Var(restoredDraft.meaning)
  private val exampleVar = Var(restoredDraft.example)
  private val meaningIsJokeVar = Var(false)
  private val exampleIsJokeVar = Var(false)
  private val isMeaningOpen = Var(false)
  private val isExampleOpen = Var(false)

  // サーバーから確定させたID
  private val selectedWordId = Var(Option.empty[Long])
  private val selectedTagId = Var(Option.empty[Long])

  // 【重要】サジェスト選択時や自動名寄せ時の「生の名前」を保持。
  // スペース入力時に「既存」状態を維持するために使用。
  private val lastResolvedWordName = Var("")
  private val lastResolvedTagName = Var("")

  private val wordSuggestions = Var(Seq.empty[SuggestionItem])
  private val tagSuggestions = Var(Seq.empty[SuggestionItem])

  private val isExistingPair = Var(false)
  private val isSubmitting = Var(false)
  private val MaxCharCount = 1000

  private def saveDraft(): Unit =
    DraftStorage.savePostDraft(
      DraftStorage.PostDraft(wordVar.now(), readingVar.now(), tagVar.now(), meaningVar.now(), exampleVar.now())
    )

  private def renderJokeToggle(jokeVar: Var[Boolean], labelText: String) = div(
    cls := "flex items-center justify-between p-3 bg-orange-50/20 dark:bg-orange-900/20 rounded-xl border border-orange-100/80 dark:border-orange-800/50 mt-2",
    label(
      cls := "flex items-center cursor-pointer group flex-1",
      input(
        tpe := "checkbox",
        cls := "appearance-none relative w-11 h-6 bg-gray-200 dark:bg-gray-700 rounded-full cursor-pointer transition-colors checked:bg-orange-500 focus:outline-none before:content-[''] before:absolute before:top-[2px] before:left-[2px] before:bg-white before:w-5 before:h-5 before:rounded-full before:transition-transform checked:before:left-[calc(100%-1.25rem-2px)]",
        controlled(checked <-- jokeVar.signal, onClick.mapToChecked --> jokeVar)
      ),
      span(cls := "ml-4 text-sm font-semibold text-gray-700 dark:text-gray-300", labelText)
    ),
    span(
      cls := "text-[10px] text-orange-500 dark:text-orange-400 font-medium leading-tight text-right w-24",
      "ユーモアを含む場合にON"
    )
  )

  private def renderOptionalAccordion(
      title: String,
      description: String,
      state: Var[String],
      isOpen: Var[Boolean],
      placeholder: String,
      jokeVar: Var[Boolean],
      jokeLabel: String
  ): HtmlElement = {
    val hasContentSignal = state.signal.map(_.trim.nonEmpty)

    div(
      cls := "rounded-2xl border border-gray-100 dark:border-gray-800 bg-white dark:bg-gray-900 shadow-sm overflow-hidden",
      button(
        tpe := "button",
        cls := "w-full flex items-center justify-between gap-3 p-4 text-left hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors",
        onClick --> { _ => isOpen.update(open => !open) },
        div(
          cls := "min-w-0 flex-1",
          div(
            cls := "flex items-center gap-2",
            span(cls := "text-sm font-black text-gray-900 dark:text-gray-100", title),
            span(
              cls := "shrink-0 rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-[10px] font-bold text-gray-500 dark:text-gray-400",
              "任意"
            ),
            child <-- hasContentSignal.map { hasContent =>
              if (hasContent)
                span(
                  cls := "shrink-0 rounded-full bg-blue-50 dark:bg-blue-900/30 px-2 py-0.5 text-[10px] font-bold text-blue-600 dark:text-blue-300",
                  "入力あり"
                )
              else emptyNode
            }
          ),
          p(cls := "mt-1 text-xs text-gray-500 dark:text-gray-400 leading-relaxed", description)
        ),
        svg.svg(
          svg.cls <-- isOpen.signal.map(open =>
            s"w-5 h-5 text-gray-400 dark:text-gray-500 transition-transform ${if (open) "rotate-180" else ""}"
          ),
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.viewBox := "0 0 24 24",
          svg.path(svg.d := "M19 9l-7 7-7-7")
        )
      ),
      child <-- isOpen.signal.map { open =>
        if (open) {
          div(
            cls := "space-y-2 border-t border-gray-100 dark:border-gray-800 p-4",
            textAreaField(placeholder, state, MaxCharCount, _ => saveDraft()),
            div(
              cls := "flex items-center justify-between",
              span(cls := "text-[10px] text-gray-400 dark:text-gray-500", "空欄のままでも投稿できます"),
              span(
                cls <-- state.signal.map(text =>
                  if (text.length > MaxCharCount) "text-red-500 dark:text-red-400 font-bold text-[10px]"
                  else "text-gray-400 dark:text-gray-500 text-[10px]"
                ),
                child.text <-- state.signal.map(text => s"${text.length} / $MaxCharCount")
              )
            ),
            renderJokeToggle(jokeVar, jokeLabel)
          )
        } else emptyNode
      }
    )
  }

  def apply(): HtmlElement = {
    if (AuthStorage.getToken.isDefined) {
      mainTag(
        cls := "w-full mx-auto bg-white dark:bg-gray-900 min-h-screen pb-40 shadow-sm pt-10",
        div(
          cls := "px-4 md:px-8 space-y-10",
          sectionTag(
            cls := "space-y-6",
            sectionTitle("言葉・タグの指定"),

            // 言葉入力
            div(
              cls := "space-y-2 relative",
              renderLabel("言葉", required = true),
              div(
                cls := "relative",
                inputWithSuggest(wordVar, isTag = false, wordSuggestions),
                child <-- wordSuggestions.signal.map(items =>
                  renderSuggestList(items, isTag = false, wordSuggestions, wordVar)
                )
              ),
              span(
                cls <-- wordVar.signal.map(text =>
                  if (text.length > 100) "text-red-500 dark:text-red-400 font-bold text-[10px]"
                  else "text-gray-400 dark:text-gray-500 text-[10px]"
                ),
                child.text <-- wordVar.signal.map(text => s"${text.length} / 100")
              )
            ),

            // タグ入力
            div(
              cls := "space-y-2",
              renderLabel("タグ", required = true),
              div(
                cls := "relative",
                inputWithSuggest(tagVar, isTag = true, tagSuggestions),
                child <-- tagSuggestions.signal.map(items =>
                  renderSuggestList(items, isTag = true, tagSuggestions, tagVar)
                )
              ),
              span(
                cls <-- tagVar.signal.map(text =>
                  if (text.length > 100) "text-red-500 dark:text-red-400 font-bold text-[10px]"
                  else "text-gray-400 dark:text-gray-500 text-[10px]"
                ),
                child.text <-- tagVar.signal.map(text => s"${text.length} / 100")
              )
            ),

            // 読み入力
            div(
              cls := "space-y-2",
              renderLabel("読み（ひらがな・カタカナ）", required = true),
              input(
                tpe := "text",
                cls <-- isExistingPair.signal.combineWith(readingVar.signal).map { case (existing, reading) =>
                  val base = "w-full p-4 border rounded-xl outline-none text-base transition-all shadow-sm "
                  val hasInvalidChars =
                    reading.exists(c => !((c >= 'ぁ' && c <= 'ん') || (c >= 'ァ' && c <= 'ヶ') || c == 'ー'))
                  if (existing) {
                    base + "bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400 border-gray-200 dark:border-gray-700 cursor-not-allowed"
                  } else if (hasInvalidChars && reading.nonEmpty) {
                    base + "bg-red-50 dark:bg-red-900/20 text-gray-800 dark:text-gray-100 border-red-300 dark:border-red-800 focus:bg-red-50 dark:focus:bg-red-900/20 focus:border-red-400 focus:ring-4 focus:ring-red-50 dark:focus:ring-red-900/30"
                  } else {
                    base + "bg-gray-50 dark:bg-gray-800 text-gray-800 dark:text-gray-100 border-gray-100 dark:border-gray-700 focus:bg-white dark:focus:bg-gray-800 focus:border-blue-400 focus:ring-4 focus:ring-blue-50 dark:focus:ring-blue-900/30"
                  }
                },
                disabled <-- isExistingPair.signal,
                placeholder := "ひらがな・カタカナで読みを入力",
                maxLength := 100,
                controlled(
                  value <-- readingVar.signal,
                  onInput.mapToValue --> { value =>
                    readingVar.set(value)
                    saveDraft()
                  }
                ),
                onBlur.mapToValue --> { v =>
                  // フォーカスを外した時にひらがな・カタカナと伸ばし棒のみにフィルタリング
                  val filtered = v.filter(c => (c >= 'ぁ' && c <= 'ん') || (c >= 'ァ' && c <= 'ヶ') || c == 'ー')
                  readingVar.set(filtered)
                }
              ),
              // エラーメッセージ表示
              child <-- readingVar.signal.map { reading =>
                val hasInvalidChars =
                  reading.exists(c => !((c >= 'ぁ' && c <= 'ん') || (c >= 'ァ' && c <= 'ヶ') || c == 'ー'))
                if (hasInvalidChars && reading.nonEmpty) {
                  div(
                    cls := "text-xs text-red-500 dark:text-red-400 mt-1 px-1",
                    "ひらがな・カタカナと伸ばし棒（ー）のみ使用できます"
                  )
                } else emptyNode
              },
              span(
                cls <-- readingVar.signal.map(text =>
                  if (text.length > 100) "text-red-500 dark:text-red-400 font-bold text-[10px]"
                  else "text-gray-400 dark:text-gray-500 text-[10px]"
                ),
                child.text <-- readingVar.signal.map(text => s"${text.length} / 100")
              )
            ),
            renderStatusAnnouncer()
          ),
          hr(cls := "border-gray-100 dark:border-gray-800"),

          // 語釈・用例入力セクション
          sectionTag(
            cls := "space-y-3",
            div(
              cls := "space-y-1",
              sectionTitle("追加情報"),
              p(cls := "text-xs text-gray-500 dark:text-gray-400 leading-relaxed", "語釈と用例は任意です。言葉・タグ・読みだけでも投稿できます。")
            ),
            renderOptionalAccordion(
              title = "語釈",
              description = "意味や定義を一緒に投稿したい場合に入力",
              state = meaningVar,
              isOpen = isMeaningOpen,
              placeholder = "定義を入力...",
              jokeVar = meaningIsJokeVar,
              jokeLabel = "この語釈はジョーク"
            ),
            renderOptionalAccordion(
              title = "用例",
              description = "使われ方や例文を一緒に投稿したい場合に入力",
              state = exampleVar,
              isOpen = isExampleOpen,
              placeholder = "使われ方を入力...",
              jokeVar = exampleIsJokeVar,
              jokeLabel = "この用例はジョーク"
            )
          )
        ),
        SubmitButton(
          isLoading = isSubmitting.signal,
          isValid = isValidSignal,
          label = "投稿する",
          action = () => handleSubmit()
        ),
        button(
          tpe := "button",
          cls := "mx-auto block text-xs text-gray-400 hover:text-red-500 dark:hover:text-red-400",
          "下書きを破棄",
          onClick --> { _ =>
            Modal.show(
              "下書きを破棄",
              "入力中の内容は復元できなくなります。破棄しますか？",
              ModalType.Confirm,
              resetFields()
            )
          }
        )
      )
    } else {
      GuestDisplay("投稿するにはログインが必要です")
    }
  }

  // --- サジェスト入力フィールドのコアロジック ---
  private def inputWithSuggest(
      state: Var[String],
      isTag: Boolean,
      suggestions: Var[Seq[SuggestionItem]]
  ): HtmlElement = {
    val maxLen = 100 // 言葉とタグは100文字制限

    // デバウンス処理（300ms）
    val debouncedFetchSuggestions = Debouncer.debounce[(String, Boolean)](300) { case (query, isTagParam) =>
      fetchSuggestions(query, isTagParam)
    }

    input(
      tpe := "text",
      cls := "w-full p-4 bg-gray-50 dark:bg-gray-800 border border-gray-100 dark:border-gray-700 rounded-xl outline-none text-base text-gray-800 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:bg-white dark:focus:bg-gray-800 focus:border-blue-400 focus:ring-4 focus:ring-blue-50 dark:focus:ring-blue-900/30 transition-all shadow-sm",
      placeholder := (if (isTag) "タグを入力または選択" else "言葉を入力または選択"),
      maxLength := maxLen,
      controlled(
        value <-- state.signal,
        onInput.mapToValue --> { v =>
          // 字数制限を超える場合は制限する
          val limited = if (v.length > maxLen) v.substring(0, maxLen) else v
          state.set(limited)
          saveDraft()
          val trimmedV = smartTrim(limited)
          val lastName = if (isTag) lastResolvedTagName.now() else lastResolvedWordName.now()

          // 1. スペースを入力しただけで「既存→新規」に切り替わるのを防ぐガード
          if (trimmedV != lastName) {
            if (isTag) {
              selectedTagId.set(None)
              lastResolvedTagName.set("")
            } else {
              selectedWordId.set(None)
              lastResolvedWordName.set("")
            }
            isExistingPair.set(false)
          }

          // 2. 入力中にサジェストリストとリアルタイムで完全一致照合
          if (trimmedV.nonEmpty) {
            suggestions.now().find(_.name == trimmedV).foreach { item =>
              applyResolvedItem(item, isTag)
            }
            debouncedFetchSuggestions((trimmedV, isTag))
          }
        }
      ),
      onFocus.mapToValue --> { v =>
        val otherVal = if (isTag) wordVar.now().trim else tagVar.now().trim
        val otherId = if (isTag) selectedWordId.now() else selectedTagId.now()

        // 相手側が入力されているがIDがない場合、フォーカス時に裏側でIDを特定してサジェストの精度を上げる
        if (otherId.isEmpty && otherVal.nonEmpty) {
          resolveIdByName(otherVal, !isTag, () => debouncedFetchSuggestions((v, isTag)))
        } else {
          debouncedFetchSuggestions((v, isTag))
        }
      },
      onBlur.mapToValue --> { v =>
        val trimmedV = smartTrim(v)
        state.set(trimmedV) // フォーカスアウト時に全角・半角スペースを自動除去

        // サジェストのクリックイベント(onMouseDown)を阻害しないよう150ms遅延
        js.timers.setTimeout(150) {
          suggestions.set(Nil)
          if ((if (isTag) selectedTagId.now() else selectedWordId.now()).isEmpty && trimmedV.nonEmpty) {
            resolveIdByName(trimmedV, isTag, () => checkPairStatus())
          } else {
            checkPairStatus()
          }
        }
      }
    )
  }

  // サジェスト選択・一致時の状態確定処理
  private def applyResolvedItem(item: SuggestionItem, isTag: Boolean): Unit = {
    if (isTag) {
      selectedTagId.set(Some(item.id))
      lastResolvedTagName.set(item.name)
    } else {
      selectedWordId.set(Some(item.id))
      lastResolvedWordName.set(item.name)
    }

    // 現在の「言葉×タグ」の組み合わせがDBに存在する場合（sourceTypeがrelated）
    if (item.sourceType == "related") {
      isExistingPair.set(true)
      item.reading.foreach(readingVar.set)
    }
  }

  // 文字列からIDを特定するユーティリティ
  private def resolveIdByName(name: String, isTagSearch: Boolean, callback: () => Unit): Unit = {
    val endpoint = if (isTagSearch) "/suggests/tags" else "/suggests/words"
    ApiClient.post[SuggestionRequest, SuggestionResponse](endpoint, SuggestionRequest(name)).foreach { res =>
      res.items.find(_.name == name).foreach { item =>
        if (isTagSearch) selectedTagId.set(Some(item.id)) else selectedWordId.set(Some(item.id))
      }
      callback()
      checkPairStatus()
    }
  }

  // 言葉とタグの両方が確定した際にペア状態を再確認
  private def checkPairStatus(): Unit = {
    val wId = selectedWordId.now()
    val tId = selectedTagId.now()
    if (wId.isDefined && tId.isDefined) {
      ApiClient
        .post[SuggestionRequest, SuggestionResponse]("/suggests/tags", SuggestionRequest(tagVar.now(), wId, None))
        .foreach { res =>
          res.items.find(_.id == tId.get).foreach { item =>
            if (item.sourceType == "related") {
              isExistingPair.set(true)
              item.reading.foreach(readingVar.set)
            }
          }
        }
    }
  }

  private def renderSuggestList(
      items: Seq[SuggestionItem],
      isTag: Boolean,
      suggestionsVar: Var[Seq[SuggestionItem]],
      state: Var[String]
  ): Node = {
    if (items.isEmpty) emptyNode
    else {
      val hasContext = if (isTag) selectedWordId.now().isDefined else selectedTagId.now().isDefined
      div(
        cls := "absolute top-full z-50 w-full mt-1 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-xl shadow-xl max-h-60 overflow-y-auto",
        items.map { item =>
          val isRelated = item.sourceType == "related"
          div(
            cls := s"p-3 flex justify-between items-center cursor-pointer transition-colors border-b border-gray-50 dark:border-gray-800 last:border-none ${
                if (hasContext && isRelated) "bg-amber-50/50 dark:bg-amber-900/20 hover:bg-amber-100 dark:hover:bg-amber-900/30"
                else "hover:bg-blue-50 dark:hover:bg-gray-800"
              }",
            div(
              span(cls := "text-sm font-bold text-gray-800 dark:text-gray-100", item.name),
              item.reading
                .map(r => span(cls := "ml-2 text-[10px] text-gray-400 dark:text-gray-500", s"($r)"))
                .getOrElse(emptyNode)
            ),
            if (hasContext && isRelated)
              span(
                cls := "text-[9px] px-1.5 py-0.5 rounded bg-amber-200 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300",
                "登録済"
              )
            else emptyNode,
            onMouseDown --> { ev =>
              ev.preventDefault()
              state.set(item.name)
              applyResolvedItem(item, isTag)
              suggestionsVar.set(Nil)
            }
          )
        }
      )
    }
  }

  private def fetchSuggestions(query: String, isTag: Boolean): Unit = {
    val req = SuggestionRequest(query.trim, selectedWordId.now(), selectedTagId.now())
    val endpoint = if (isTag) "/suggests/tags" else "/suggests/words"
    ApiClient.post[SuggestionRequest, SuggestionResponse](endpoint, req).foreach { res =>
      // タグの場合は「Lexi」を除外
      val filteredItems = if (isTag) {
        res.items.filterNot(item => item.name.equalsIgnoreCase("Lexi") || item.name.equalsIgnoreCase("#Lexi"))
      } else {
        res.items
      }
      if (isTag) tagSuggestions.set(filteredItems) else wordSuggestions.set(filteredItems)
    }
  }

  private def renderStatusAnnouncer() = child <-- isExistingPair.signal.map { existing =>
    div(
      cls := s"p-4 rounded-xl border text-xs font-medium leading-relaxed transition-all ${
          if (existing) "bg-amber-50 dark:bg-amber-900/20 text-amber-800 dark:text-amber-300 border-amber-200 dark:border-amber-800"
          else "bg-blue-50 dark:bg-blue-900/20 text-blue-800 dark:text-blue-300 border-blue-200 dark:border-blue-800"
        }",
      if (existing) "すでに存在する組み合わせです。内容は「追記」として保存されます。" else "新しい言葉の組み合わせです。まずは言葉・タグ・読みを登録しましょう。語釈や用例は後から追加できます。"
    )
  }

  // --- 保存処理の修正 ---
  private def handleSubmit(): Unit = {
    val word = util.Validation.trimAll(wordVar.now())
    val tag = util.Validation.trimAll(tagVar.now())
    val meaning = util.Validation.trimAll(meaningVar.now())
    val example = util.Validation.trimAll(exampleVar.now())

    // 空白のみのチェック
    if (word.isEmpty || tag.isEmpty) {
      Modal.show("エラー", "言葉とタグを入力してください", ModalType.Info)
      return
    }

    // 文字数制限チェック
    if (word.length > 100) {
      Modal.show("エラー", "言葉は100文字以内で入力してください", ModalType.Info)
      return
    }
    if (tag.length > 100) {
      Modal.show("エラー", "タグは100文字以内で入力してください", ModalType.Info)
      return
    }
    if (meaning.length > MaxCharCount) {
      Modal.show("エラー", s"語釈は${MaxCharCount}文字以内で入力してください", ModalType.Info)
      return
    }
    if (example.length > MaxCharCount) {
      Modal.show("エラー", s"用例は${MaxCharCount}文字以内で入力してください", ModalType.Info)
      return
    }

    // Lexiタグの入力チェック
    if (tag.equalsIgnoreCase("Lexi")) {
      Modal.show("エラー", "「Lexi」タグはシステム予約のため使用できません", ModalType.Info)
      return
    }

    // 保存前に確認モーダルを表示
    Modal.show(
      title = "投稿の確認",
      message = s"「$word」に新しい定義を保存しますか？",
      mType = ModalType.Confirm,
      action = {
        // 「はい」が押された時の実際の送信処理
        executePost(word, tag)
      }
    )
  }

  // 実際の送信ロジックを分離
  private def executePost(word: String, tag: String): Unit = {
    isSubmitting.set(true)

    // 送信前に全データを正規化
    val reading = util.Validation.trimAll(readingVar.now())

    val meaningEntry = Some(util.Validation.trimAll(meaningVar.now()))
      .filter(_.nonEmpty)
      .map(text => PostEntry(text, meaningIsJokeVar.now()))

    val exampleEntry = Some(util.Validation.trimAll(exampleVar.now()))
      .filter(_.nonEmpty)
      .map(text => PostEntry(text, exampleIsJokeVar.now()))

    val body = WordTagPostRequest(
      wordId = selectedWordId.now(),
      wordMain = word,
      wordReading = reading,
      tagId = selectedTagId.now(),
      tagName = tag,
      meaning = meaningEntry,
      example = exampleEntry
    )

    ApiClient.post[WordTagPostRequest, WordTagPostResponse]("/words", body).onComplete {
      case scala.util.Success(res) =>
        isSubmitting.set(false)
        resetFields()
        val initialTab =
          if (res.hasMeaning) WordDetailTab.Meaning
          else if (res.hasExample) WordDetailTab.Example
          else WordDetailTab.Meaning
        Toast.show("登録しました")
        Router.router.pushState(WordDetailPage(res.wordId, res.tagId, initialTab))

      case scala.util.Failure(ex) =>
        isSubmitting.set(false)
        // エラー詳細を少し詳しく表示（またはコンソール確認）
        org.scalajs.dom.console.error(ex)
        Modal.show("エラー", "保存に失敗しました。入力内容を確認してください。", ModalType.Info)
    }
  }

  private def resetFields(): Unit = {
    wordVar.set(""); readingVar.set(""); tagVar.set(""); meaningVar.set(""); exampleVar.set("")
    selectedWordId.set(None); selectedTagId.set(None); isExistingPair.set(false)
    meaningIsJokeVar.set(false); exampleIsJokeVar.set(false)
    lastResolvedWordName.set(""); lastResolvedTagName.set("")
    DraftStorage.discard("post")
  }

  // --- バリデーションロジック ---
  private val isValidSignal: Signal[Boolean] = Signal
    .combine(
      wordVar.signal,
      readingVar.signal,
      tagVar.signal,
      meaningVar.signal,
      exampleVar.signal,
      isExistingPair.signal
    )
    .map { (word, reading, tag, meaning, example, existing) =>
      val trimmedW = util.Validation.trimAll(word)
      val trimmedT = util.Validation.trimAll(tag)
      val trimmedR = util.Validation.trimAll(reading)
      val trimmedM = util.Validation.trimAll(meaning)
      val trimmedE = util.Validation.trimAll(example)

      val hasBase = trimmedW.nonEmpty && trimmedR.nonEmpty && trimmedT.nonEmpty

      // 【重要】末尾に1つでもスペースがある場合は、既存・新規問わず「不正な入力」として保存不可にする
      val isPureInput = word == trimmedW && tag == trimmedT && meaning == trimmedM && example == trimmedE

      // 文字数制限チェック（言葉・タグは100文字、語釈・用例は1000文字）
      val withinLimits = trimmedW.length <= 100 && trimmedT.length <= 100 &&
        trimmedM.length <= MaxCharCount && trimmedE.length <= MaxCharCount

      hasBase && isPureInput && withinLimits
    }

  // 全角スペース対応版トリミング
  private def smartTrim(s: String): String =
    s.replaceAll("^[\u0000-\u0020\u3000]+", "").replaceAll("[\u0000-\u0020\u3000]+$", "")
}
