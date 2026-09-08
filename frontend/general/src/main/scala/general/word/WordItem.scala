package general.word

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import general.ui.ReportModal
import general.ui.EditModal
import general.ui.DropdownMenu
import general.auth.AuthStorage
import models.*
import shared.api.*
import org.scalajs.dom
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

object WordItem {
  private val baseCls =
    "p-4 bg-white dark:bg-gray-900 border border-gray-100 dark:border-gray-800 rounded-2xl shadow-sm space-y-4 relative"

  def meaningCard(
      vm: MeaningViewValue,
      isMy: Boolean,
      onFav: Option[Boolean => Unit] = None,
      onDelete: Option[Long => Unit] = None,
      onEdit: Option[(Long, String, Boolean) => Unit] = None,
      onReactionChange: Option[(Long, String, Boolean, Int) => Unit] = None,
      onQuote: Option[() => Unit] = None
  ): HtmlElement =
    render(
      vm.meaning.meaningText,
      vm.userName,
      vm.meaning.meaningPostedDate,
      vm,
      "1",
      vm.meaning.meaningId.getOrElse(-1L),
      vm.meaning.wordId,
      vm.meaning.tagId,
      isMy,
      onFav,
      onDelete,
      onEdit,
      onReactionChange = onReactionChange,
      onQuote = onQuote
    )

  def exampleCard(
      vm: ExampleViewValue,
      isMy: Boolean,
      onFav: Option[Boolean => Unit] = None,
      onDelete: Option[Long => Unit] = None,
      onEdit: Option[(Long, String, Boolean) => Unit] = None,
      onReactionChange: Option[(Long, String, Boolean, Int) => Unit] = None,
      onQuote: Option[() => Unit] = None
  ): HtmlElement =
    render(
      s""""${vm.example.exampleText}"""",
      vm.userName,
      vm.example.examplePostedDate,
      vm,
      "2",
      vm.example.exampleId.getOrElse(-1L),
      vm.example.wordId,
      vm.example.tagId,
      isMy,
      onFav,
      onDelete,
      onEdit,
      isItalic = true,
      onReactionChange = onReactionChange,
      onQuote = onQuote
    )

  private def render(
      text: String,
      user: String,
      date: Long,
      vm: Any,
      cType: String,
      cId: Long,
      wId: Long,
      tId: Long,
      isMy: Boolean,
      onFav: Option[Boolean => Unit],
      onDelete: Option[Long => Unit] = None,
      onEdit: Option[(Long, String, Boolean) => Unit] = None,
      isItalic: Boolean = false,
      onReactionChange: Option[(Long, String, Boolean, Int) => Unit] = None,
      onQuote: Option[() => Unit] = None
  ): HtmlElement = {
    // displayIdを型安全に取得
    val displayIdOpt = vm match {
      case m: MeaningViewValue => m.displayId
      case e: ExampleViewValue => e.displayId
    }
    // isJokeフラグを型安全に取得
    val isJoke = vm match {
      case m: MeaningViewValue => m.meaning.isJoke
      case e: ExampleViewValue => e.example.isJoke
    }
    // 状態の抽出
    val (isG, gC, isH, hC, isF, fC, isFavInit) = vm match {
      case m: MeaningViewValue =>
        (
          m.isGood,
          m.meaning.meaningGoodCount,
          m.isHelpful,
          m.meaning.meaningHelpfulCount,
          m.isFunny,
          m.meaning.meaningFunnyCount,
          m.isFavorite
        )
      case e: ExampleViewValue =>
        (
          e.isGood,
          e.example.exampleGoodCount,
          e.isHelpful,
          e.example.exampleHelpfulCount,
          e.isFunny,
          e.example.exampleFunnyCount,
          e.isFavorite
        )
    }

    val good = Var((isG, gC)); val help = Var((isH, hC)); val funny = Var((isF, fC))
    val isFavVar = Var(isFavInit)

    // ラベル情報
    val (label, maxLength) = cType match {
      case "1" => ("語釈", 1000)
      case "2" => ("用例", 1000)
      case _   => ("投稿", 1000)
    }

    div(
      idAttr := s"${if (cType == "1") "meaning" else "example"}-$cId",
      cls := baseCls,
      // 自分の投稿なら編集メニューを表示
      if (isMy) {
        (onDelete, onEdit) match {
          case (Some(deleteFn), Some(editFn)) =>
            div(
              cls := "absolute top-4 right-2",
              DropdownMenu(
                Seq(
                  DropdownMenu.MenuItem(
                    "編集",
                    () => {
                      val cleanText = if (isItalic) {
                        // 用例の場合は引用符を除去
                        text.stripPrefix(""").stripSuffix(""")
                      } else {
                        text
                      }
                      EditModal.show(
                        EditModal.EditConfig(
                          title = s"${label}を編集",
                          fieldLabel = label,
                          initialValue = cleanText,
                          maxLength = maxLength,
                          onSave = (newText: String) => editFn(cId, newText, false), // 後方互換性のため
                          initialIsJoke = isJoke,
                          onSaveWithJoke =
                            Some((newText: String, newIsJoke: Boolean) => editFn(cId, newText, newIsJoke))
                        )
                      )
                    }
                  ),
                  DropdownMenu.MenuItem(
                    "削除",
                    () => {
                      Modal.show(
                        "削除の確認",
                        s"この${label}を削除してもよろしいですか？",
                        ModalType.Confirm,
                        deleteFn(cId)
                      )
                    },
                    isDestructive = true
                  )
                )
              )
            )
          case _ =>
            button(
              cls := "absolute top-4 right-2 p-1 text-gray-400 dark:text-gray-500 active:bg-gray-100 dark:active:bg-gray-800 rounded-full",
              svg.svg(
                svg.cls := "w-5 h-5",
                svg.fill := "currentColor",
                svg.viewBox := "0 0 24 24",
                svg.circle(svg.cx := "12", svg.cy := "5", svg.r := "2"),
                svg.circle(svg.cx := "12", svg.cy := "12", svg.r := "2"),
                svg.circle(svg.cx := "12", svg.cy := "19", svg.r := "2")
              ),
              onClick --> (_ => dom.window.alert("編集・削除メニュー"))
            )
        }
      } else if (!AuthStorage.getToken.isEmpty) {
        div(
          cls := "absolute top-4 right-2",
          DropdownMenu(
            Seq(
              DropdownMenu.MenuItem(
                "報告",
                () => {
                  ReportModal.show(
                    ReportModal.ReportConfig(
                      contentTypeId = cType,
                      contentId = cId,
                      contentDescription = if (cType == "1") "この語釈" else "この用例"
                    )
                  )
                },
                isDestructive = false
              )
            )
          )
        )
      } else emptyNode,
      p(
        cls := s"text-gray-800 dark:text-gray-100 leading-relaxed pr-6 ${
            if (isItalic) "italic text-gray-600 dark:text-gray-300" else ""
          }",
        text
      ),

      // Footer
      div(
        cls := "space-y-3",
        div(
          cls := "flex justify-between items-center text-[10px]",
          div(
            cls := "flex items-center space-x-2",
            span(cls := "text-gray-400 dark:text-gray-500", s"${new js.Date(date.toDouble).toLocaleDateString()}"),
            if (isJoke) {
              span(cls := "px-1.5 py-0.5 bg-orange-500 text-white font-bold rounded-full text-[9px]", "ジョーク")
            } else emptyNode
          ),
          span(
            cls := (if (isMy) "text-blue-600 dark:text-blue-400 font-bold" else "text-gray-400 dark:text-gray-500"),
            // displayId があれば "名前 @ID"、なければユーザー名のみ
            displayIdOpt.fold(s"投稿者: $user")(id => s"投稿者: $user @$id")
          )
        ),
        div(
          dataAttr("word-item-actions") := cType,
          cls := "flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-gray-100 dark:border-gray-800",
          div(
            dataAttr("reaction-actions") := cType,
            cls := "flex w-full flex-wrap items-center gap-1.5 sm:w-auto sm:flex-1",
            reactionBtn("good", good, cType, cId, "good", onReactionChange),
            reactionBtn("helpful", help, cType, cId, "helpful", onReactionChange),
            reactionBtn("funny", funny, cType, cId, "funny", onReactionChange)
          ),
          div(
            dataAttr("secondary-actions") := cType,
            cls := "ml-auto flex shrink-0 items-center gap-1",
            onQuote
              .filter(_ => AuthStorage.getToken.isDefined)
              .map(action =>
                button(
                  tpe := "button",
                  aria.label := s"この${label}を引用",
                  cls := "shrink-0 whitespace-nowrap rounded-lg px-2 py-1.5 text-[10px] font-bold text-orange-600 hover:bg-orange-50 dark:text-orange-300 dark:hover:bg-orange-950/30",
                  "引用",
                  onClick.stopPropagation --> (_ => action())
                )
              )
              .getOrElse(emptyNode),
            if (!isMy) favBtn(isFavVar, cType, cId, onFav) else emptyNode
          )
        )
      )
    )
  }

  private def favBtn(isFav: Var[Boolean], cType: String, cId: Long, onToggle: Option[Boolean => Unit]): HtmlElement = {
    val processing = Var(false)
    val isGuest = AuthStorage.getToken.isEmpty
    button(
      tpe := "button",
      aria.label := "お気に入り",
      cls <-- isFav.signal.map(f =>
        s"shrink-0 p-2 transition-all ${if (f) "text-yellow-500 scale-110" else "text-gray-300 dark:text-gray-600"}"
      ),
      disabled <-- processing.signal,
      svg.svg(
        svg.cls := "w-6 h-6",
        svg.fill <-- isFav.signal.map(if (_) "currentColor" else "none"),
        svg.stroke <-- isFav.signal.map(if (_) "currentColor" else "currentColor"),
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 24 24",
        svg.path(
          svg.d := "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
        )
      ),
      onClick.stopImmediatePropagation --> { _ =>
        if (isGuest) {
          Modal.show("ログインが必要です", "お気に入り機能を利用するにはログインしてください。", ModalType.Info)
        } else {
          processing.set(true)
          util.ApiClient
            .post[FavoriteToggleRequest, FavoriteToggleResponse](
              "/user/favorites/toggle",
              FavoriteToggleRequest(cType, cId)
            )
            .onComplete { res =>
              processing.set(false)
              res.foreach { r => isFav.set(r.isFavorite); onToggle.foreach(_(r.isFavorite)) }
            }
        }
      }
    )
  }

  private def reactionBtn(
      icon: String,
      state: Var[(Boolean, Int)],
      cType: String,
      cId: Long,
      rType: String,
      onReactionChange: Option[(Long, String, Boolean, Int) => Unit] = None
  ): HtmlElement = {
    val processing = Var(false)
    val isGuest = AuthStorage.getToken.isEmpty

    val (iconPath, label) = rType match {
      case "good" =>
        (
          "M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-1a2 2 0 00-2 2v5",
          "いいね"
        )
      case "helpful" => ("M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z", "参考")
      case "funny"   => ("M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z", "面白い")
      case _         => ("", "")
    }

    button(
      tpe := "button",
      dataAttr("reaction") := rType,
      cls <-- state.signal.map { s =>
        val activeCls = rType match {
          case "good" =>
            "bg-rose-50 dark:bg-rose-900/30 text-rose-600 dark:text-rose-300 ring-1 ring-rose-200 dark:ring-rose-800 scale-105 shadow-sm"
          case "helpful" =>
            "bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-300 ring-1 ring-blue-200 dark:ring-blue-800 scale-105 shadow-sm"
          case "funny" =>
            "bg-amber-50 dark:bg-amber-900/30 text-amber-600 dark:text-amber-300 ring-1 ring-amber-200 dark:ring-amber-800 scale-105 shadow-sm"
          case _ =>
            "bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 ring-1 ring-blue-200 dark:ring-blue-800 scale-105 shadow-sm"
        }
        s"flex shrink-0 items-center space-x-1 whitespace-nowrap px-2 py-1.5 rounded-full text-xs font-bold transition-all duration-150 active:scale-95 sm:px-2.5 ${
            if (s._1) activeCls
            else "bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700"
          }"
      },
      disabled <-- processing.signal,
      svg.svg(
        svg.cls <-- state.signal.map(s =>
          s"w-4 h-4 transition-transform duration-150 ${if (s._1) "scale-110" else ""}"
        ),
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 24 24",
        svg.path(svg.d := iconPath)
      ),
      span(cls := "text-[10px]", label),
      span(child.text <-- state.signal.map(_._2.toString)),
      onClick --> { _ =>
        if (isGuest) {
          Modal.show("ログインが必要です", "リアクション機能を利用するにはログインしてください。", ModalType.Info)
        } else {
          processing.set(true)
          util.ApiClient
            .post[ReactionToggleRequest, ReactionToggleResponse](
              "/reactions/toggle",
              ReactionToggleRequest(cType, cId, rType)
            )
            .onComplete { res =>
              processing.set(false)
              res.foreach { r =>
                state.set((r.active, r.count))
                onReactionChange.foreach(_(cId, rType, r.active, r.count))
              }
            }
        }
      }
    )
  }
}
