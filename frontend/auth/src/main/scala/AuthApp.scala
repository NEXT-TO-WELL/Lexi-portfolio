import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.JSON
import general.auth.AuthStorage
import scala.concurrent.ExecutionContext.Implicits.global

object AuthApp {
  sealed trait Mode
  case object SignIn extends Mode
  case object SignUp extends Mode

  private val modeVar = Var[Mode](SignIn)
  private val userIdVar = Var("")
  private val passwordVar = Var("")
  private val confirmPasswordVar = Var("") // パスワード確認用
  private val userNameVar = Var("") // 追加: ユーザー名用のVar

  private def getCsrfToken: String = {
    // querySelectorの結果をOptionで包んで安全に処理
    Option(dom.document.querySelector("meta[name='csrf-token']"))
      .map(_.getAttribute("content"))
      .getOrElse {
        dom.console.error("CSRFトークンが見つかりません！HTMLのmetaタグを確認してください。")
        ""
      }
  }
  def apply(): HtmlElement = {
    // 1. PC全体の背景色と中央寄せの設定
    div(
      cls := "min-h-screen bg-gray-100 dark:bg-gray-950 flex justify-center items-center",

      // 2. スマホ枠の再現 (Mobile.scala と一致させる)
      // w-full max-w-[500px] で PC 時の幅を固定し、shadow と ring で境界を明確にする
      div(
        idAttr := "auth-frame",
        cls := "w-full max-w-[500px] h-[100dvh] bg-white dark:bg-gray-900 relative flex flex-col shadow-2xl ring-1 ring-gray-200 dark:ring-gray-800 overflow-hidden",

        // 3. 内部コンテンツ（スクロール可能）
        div(
          cls := "flex-grow overflow-y-auto px-8 pt-12 pb-12",

          // ロゴセクション
          div(
            cls := "flex flex-col items-center mb-10",
            img(
              idAttr := "auth-logotype",
              src := "/assets/images/lexi-logotype.svg",
              cls := "h-20 sm:h-24 w-auto mb-2 object-contain",
              alt := "Lexi"
            )
          ),

          // フォームエリア（スマホ枠が白なので、カード形式にせずフラットに配置）
          div(
            cls := "w-full space-y-6",
            h1(
              cls := "text-2xl font-black text-gray-800 dark:text-gray-100 mb-8 text-center",
              child.text <-- modeVar.signal.map(m => if (m == SignIn) "ログイン" else "新規登録")
            ),

            div(
              cls := "space-y-5",
              child.maybe <-- modeVar.signal.map {
                case SignUp =>
                  Some(
                    div(
                      cls := "space-y-1",
                      label(cls := "text-xs font-bold text-gray-400 dark:text-gray-500 ml-1", "表示名"),
                      input(
                        tpe := "text",
                        placeholder := "例: Lexiユーザー",
                        cls := "w-full bg-gray-50 dark:bg-gray-800 border border-transparent dark:border-gray-700 p-4 rounded-2xl focus:ring-2 focus:ring-blue-500 outline-none transition text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500",
                        onInput.mapToValue --> userNameVar
                      )
                    )
                  )
                case SignIn => None
              },

              div(
                cls := "space-y-1",
                label(cls := "text-xs font-bold text-gray-400 dark:text-gray-500 ml-1", "ログインID"),
                input(
                  tpe := "text",
                  placeholder := "ログインIDを入力",
                  cls := "w-full bg-gray-50 dark:bg-gray-800 border border-transparent dark:border-gray-700 p-4 rounded-2xl focus:ring-2 focus:ring-blue-500 outline-none transition text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500",
                  onInput.mapToValue --> userIdVar
                )
              ),

              div(
                cls := "space-y-1",
                label(cls := "text-xs font-bold text-gray-400 dark:text-gray-500 ml-1", "パスワード"),
                input(
                  tpe := "password",
                  placeholder := "パスワードを入力",
                  cls := "w-full bg-gray-50 dark:bg-gray-800 border border-transparent dark:border-gray-700 p-4 rounded-2xl focus:ring-2 focus:ring-blue-500 outline-none transition text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500",
                  onInput.mapToValue --> passwordVar
                )
              ),

              // 新規登録時のみパスワード確認入力を表示
              child.maybe <-- modeVar.signal.map {
                case SignUp =>
                  Some(
                    div(
                      cls := "space-y-1",
                      label(cls := "text-xs font-bold text-gray-400 dark:text-gray-500 ml-1", "パスワード（確認）"),
                      input(
                        tpe := "password",
                        placeholder := "もう一度パスワードを入力",
                        cls := "w-full bg-gray-50 dark:bg-gray-800 border border-transparent dark:border-gray-700 p-4 rounded-2xl focus:ring-2 focus:ring-blue-500 outline-none transition text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500",
                        onInput.mapToValue --> confirmPasswordVar
                      )
                    )
                  )
                case SignIn => None
              },

              button(
                tpe := "button",
                cls := "w-full py-4 rounded-2xl font-bold shadow-lg transition-all mt-4",
                cls <-- modeVar.signal
                  .combineWith(userIdVar.signal, passwordVar.signal, userNameVar.signal, confirmPasswordVar.signal)
                  .map { case (mode, userId, password, userName, confirmPassword) =>
                    val isValid = mode match {
                      case SignIn => userId.trim.nonEmpty && password.trim.nonEmpty
                      case SignUp =>
                        userId.trim.nonEmpty && password.trim.nonEmpty && userName.trim.nonEmpty && confirmPassword.trim.nonEmpty
                    }
                    if (isValid)
                      "bg-blue-600 dark:bg-blue-500 text-white shadow-blue-200 dark:shadow-black/30 hover:bg-blue-700 dark:hover:bg-blue-600 active:scale-[0.98] cursor-pointer"
                    else "bg-gray-300 dark:bg-gray-800 text-gray-500 dark:text-gray-500 cursor-not-allowed"
                  },
                disabled <-- modeVar.signal
                  .combineWith(userIdVar.signal, passwordVar.signal, userNameVar.signal, confirmPasswordVar.signal)
                  .map { case (mode, userId, password, userName, confirmPassword) =>
                    mode match {
                      case SignIn => userId.trim.isEmpty || password.trim.isEmpty
                      case SignUp =>
                        userId.trim.isEmpty || password.trim.isEmpty || userName.trim.isEmpty || confirmPassword.trim.isEmpty
                    }
                  },
                child.text <-- modeVar.signal.map(m => if (m == SignIn) "ログイン" else "アカウント作成"),
                onClick.preventDefault.stopPropagation --> { _ => performAuth() }
              ),

              div(
                cls := "pt-6 text-center",
                span(
                  cls := "text-sm text-gray-400 dark:text-gray-500",
                  child.text <-- modeVar.signal.map(m => if (m == SignIn) "アカウントをお持ちでないですか？" else "既にアカウントをお持ちですか？")
                ),
                button(
                  cls := "ml-2 text-sm font-bold text-blue-600 dark:text-blue-400 hover:underline",
                  child.text <-- modeVar.signal.map(m => if (m == SignIn) "新規登録" else "ログイン"),
                  onClick --> { _ =>
                    userIdVar.set("")
                    passwordVar.set("")
                    confirmPasswordVar.set("")
                    userNameVar.set("")
                    modeVar.update(m => if (m == SignIn) SignUp else SignIn)
                  }
                )
              ),
              // --- 追加: ゲスト閲覧リンク ---
              div(
                cls := "flex items-center justify-center space-x-2 pt-2 border-t border-gray-100 dark:border-gray-800",
                button(
                  tpe := "button",
                  cls := "text-sm font-bold text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition flex items-center",
                  "ゲストユーザーで閲覧する",
                  svg.svg(
                    svg.cls := "w-4 h-4 ml-1",
                    svg.fill := "none",
                    svg.stroke := "currentColor",
                    svg.strokeWidth := "2",
                    svg.viewBox := "0 0 24 24",
                    svg.path(svg.d := "M9 5l7 7-7 7")
                  ),
                  onClick --> { _ =>
                    // ゲストとして初期化（必要に応じてAuthStorageをクリア）
                    AuthStorage.logout()
                    dom.window.location.href = "/home"
                  }
                )
              )
            )
          )
        )
      )
    )
  }

  private def performAuth(): Unit = {
    val isLogin = modeVar.now() == SignIn

    // 新規登録時のバリデーション
    if (!isLogin) {
      val userName = userNameVar.now()
      val password = passwordVar.now()
      val confirmPassword = confirmPasswordVar.now()

      // ユーザー名のバリデーション（全角スペース含む）
      if (!util.Validation.notBlank(userName)) {
        showPopup("エラー", "ユーザー名に空白のみは使用できません", isSuccess = false) {}
        return
      }

      if (password != confirmPassword) {
        showPopup("エラー", "パスワードが一致しません", isSuccess = false) {}
        return
      }

      if (password.length < 8) {
        showPopup("エラー", "パスワードは8文字以上で入力してください", isSuccess = false) {}
        return
      }
    }

    val userId = userIdVar.now().trim
    val url =
      if (!isLogin) "/api/signUp"
      else if (userId == "lexi_admin") "/api/admin/signIn"
      else "/api/signIn"

    // 送信データの作成
    val payload = js.Dictionary(
      "userId" -> userId,
      "password" -> passwordVar.now()
    )
    // 新規登録時のみ userName を追加（全角スペースもトリミング）
    if (!isLogin) payload.update("userName", util.Validation.trimAll(userNameVar.now()))

    dom
      .fetch(
        url,
        new dom.RequestInit {
          method = dom.HttpMethod.POST
          headers = js.Dictionary(
            "Content-Type" -> "application/json",
            "Csrf-Token" -> getCsrfToken
          )
          body = JSON.stringify(payload)
        }
      )
      .toFuture
      .foreach { res =>
        if (res.status == 200 || res.status == 201) {
          res.json().toFuture.foreach { json =>
            val jsJson = json.asInstanceOf[js.Dynamic]

            // token と userId が存在するか確認
            val token = if (!js.isUndefined(jsJson.token)) Some(jsJson.token.asInstanceOf[String]) else None
            // Longで返ってきてもStringとして取得してlocalStorageに保存する
            val userId = if (!js.isUndefined(jsJson.userId)) Some(jsJson.userId.toString) else None
            // ★ role を取得（roleIdではなくrole）
            val role = if (!js.isUndefined(jsJson.role)) jsJson.role.toString else "user"

            (token, userId) match {
              case (Some(t), Some(uid)) =>
                AuthStorage.saveAuth(
                  token = t,
                  userId = uid, // This is a String now
                  roleId = role
                )
                // 管理者の場合は/managerへ、それ以外は/homeへ
                val redirectUrl = if (role == "admin") "/manager" else "/home"

                showPopup("成功", if (isLogin) "ログインしました！" else "アカウントを作成しました！", isSuccess = true) {
                  dom.window.location.href = redirectUrl
                }
              case _ =>
                dom.window.console.error("ログインレスポンスにtokenまたはuserIdがありません:", json)
                showPopup("エラー", "ログインレスポンスの解析に失敗しました。", isSuccess = false) {}
            }
          }
        } else {
          res.text().toFuture.foreach { text =>
            // JSONレスポンスからエラーメッセージを抽出
            try {
              val errorJson = js.JSON.parse(text).asInstanceOf[js.Dynamic]
              val errorMessage = if (!js.isUndefined(errorJson.message)) {
                errorJson.message.asInstanceOf[String]
              } else if (!js.isUndefined(errorJson.error)) {
                errorJson.error.asInstanceOf[String]
              } else {
                "ユーザーIDまたはパスワードが正しくありません"
              }
              showPopup("ログイン失敗", errorMessage, isSuccess = false) {}
            } catch {
              case _: Exception =>
                showPopup("ログイン失敗", "ユーザーIDまたはパスワードが正しくありません", isSuccess = false) {}
            }
          }
        }
      }
  }

  // コンポーネントを使わずに直接DOMを生成するポップアップ関数
  private def showPopup(title: String, message: String, isSuccess: Boolean)(onClose: => Unit): Unit = {
    val overlay = dom.document.createElement("div")
    // ★ 修正: fixed -> absolute に変更。inset-0 でスマホ枠いっぱいに広がる。
    overlay.setAttribute(
      "class",
      "absolute inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm p-6"
    )

    val bgColor = if (isSuccess) "bg-blue-600 dark:bg-blue-500" else "bg-red-600 dark:bg-red-500"
    val borderColor = if (isSuccess) "border-blue-200 dark:border-blue-800" else "border-red-200 dark:border-red-800"

    overlay.innerHTML = s"""
      <div class="bg-white dark:bg-gray-900 rounded-3xl p-6 w-full max-w-[320px] shadow-2xl border-2 $borderColor transform scale-100 transition-transform animate-in zoom-in-95 duration-200">
        <div class="flex flex-col items-center text-center space-y-3">
          <div class="flex-shrink-0 p-3 bg-gray-50 dark:bg-gray-800 rounded-full">
            ${
        if (isSuccess)
          """<svg class="w-8 h-8 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                   <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                 </svg>"""
        else
          """<svg class="w-8 h-8 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                   <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                 </svg>"""
      }
          </div>
          <div class="w-full">
            <h3 class="text-xl font-black text-gray-900 dark:text-gray-100 mb-2">$title</h3>
            <p class="text-sm text-gray-500 dark:text-gray-400 leading-relaxed">$message</p>
          </div>
        </div>
        <button id="popup-close-btn" class="w-full mt-8 py-4 $bgColor text-white font-bold rounded-2xl shadow-lg hover:opacity-90 transition active:scale-95 text-sm">
          確認
        </button>
      </div>
    """

    // ★ 修正: bodyではなく、スマホ枠（auth-frame）に追加する
    val container = dom.document.getElementById("auth-frame")
    if (container != null) container.appendChild(overlay)
    else dom.document.body.appendChild(overlay) // 万が一見つからない時のフォールバック

    dom.document
      .getElementById("popup-close-btn")
      .addEventListener(
        "click",
        (_: dom.Event) => {
          // 修正: parentNode を使用
          Option(overlay.parentNode).foreach(_.removeChild(overlay))
          onClose
        }
      )

    if (!isSuccess) {
      overlay.addEventListener(
        "click",
        (e: dom.Event) => {
          if (e.target == overlay) {
            // 修正: parentNode を使用
            Option(overlay.parentNode).foreach(_.removeChild(overlay))
            onClose
          }
        }
      )
    }
  }

  def main(args: Array[String]): Unit = {
    val container = dom.document.getElementById("signIn-app")
    if (container != null) render(container, apply())
  }
}
