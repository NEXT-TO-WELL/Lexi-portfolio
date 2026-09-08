package general.ui

import com.raquo.laminar.api.L.*
import component.{Modal, ModalType}
import models.ReportRequest
import util.{ApiClient, ErrorHandler, ApiError}

object ReportModal {

  case class ReportConfig(
      contentTypeId: String,
      contentId: Long,
      contentDescription: String // "このスレッド" や "このコメント"
  )

  case class ReportModalState(
      isOpen: Boolean = false,
      config: ReportConfig = ReportConfig("", 0L, ""),
      selectedReportTypeId: String = "1" // デフォルトはスパム
  )

  val stateVar = Var(ReportModalState())
  val isSubmitting = Var(false)

  // 報告理由の選択肢
  val reportTypes = List(
    ("1", "スパム"),
    ("2", "不適切なコンテンツ"),
    ("3", "誤情報"),
    ("4", "ハラスメント"),
    ("5", "その他")
  )

  def show(config: ReportConfig): Unit = {
    stateVar.set(ReportModalState(true, config, "1"))
    isSubmitting.set(false)
  }

  def close(): Unit = {
    stateVar.update(_.copy(isOpen = false))
  }

  def apply(): HtmlElement = {
    div(
      display <-- stateVar.signal.map(s => if (s.isOpen) "flex" else "none"),
      // fixed から absolute へ変更
      cls := "absolute inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm",

      // 背景オーバーレイ
      div(
        cls := "absolute inset-0",
        onClick --> { _ => close() }
      ),

      // モーダル本体
      div(
        // max-w-lg から max-w-[340px] などに調整してスマホ枠（500px）に合わせる
        cls := "relative w-full max-w-[340px] mx-4 bg-white dark:bg-gray-900 rounded-3xl shadow-2xl border border-transparent dark:border-gray-800 p-6 z-10 animate-in zoom-in-95 duration-200",
        onClick.stopPropagation --> { _ => () },

        h2(cls := "text-xl font-black text-gray-900 dark:text-gray-100 mb-2", "報告"),
        p(
          cls := "text-xs text-gray-500 dark:text-gray-400 mb-6 leading-relaxed",
          child.text <-- stateVar.signal.map(s => s"${s.config.contentDescription}を報告する理由を選択してください")
        ),

        div(
          cls := "space-y-2 mb-8",
          reportTypes.map { case (typeId, typeName) =>
            label(
              // デザインをモダンに調整
              cls := "flex items-center p-4 bg-gray-50 dark:bg-gray-800 rounded-2xl cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700 transition",
              input(
                tpe := "radio",
                nameAttr := "reportType",
                value := typeId,
                cls := "w-4 h-4 text-blue-600 dark:text-blue-400 focus:ring-blue-500 border-gray-300 dark:border-gray-600",
                checked <-- stateVar.signal.map(_.selectedReportTypeId == typeId),
                onChange.mapToValue --> { tid =>
                  stateVar.update(s => s.copy(selectedReportTypeId = tid))
                }
              ),
              span(cls := "ml-3 text-sm font-bold text-gray-700 dark:text-gray-200", typeName)
            )
          }
        ),

        div(
          cls := "flex flex-col space-y-3", // ボタンを縦に並べてモバイルらしく
          button(
            tpe := "button",
            cls := "w-full py-4 bg-rose-500/90 text-white font-bold rounded-2xl shadow-sm shadow-rose-100 dark:shadow-none hover:bg-rose-500 active:scale-[0.98] transition disabled:opacity-50",
            disabled <-- isSubmitting.signal,
            child.text <-- isSubmitting.signal.map(submitting => if (submitting) "送信中..." else "報告を送信"),
            onClick --> { _ =>
              isSubmitting.set(true)
              val state = stateVar.now()
              val req = ReportRequest(
                reportTypeId = state.selectedReportTypeId,
                contentTypeId = state.config.contentTypeId,
                contentId = state.config.contentId
              )

              ErrorHandler.handle(
                ApiClient.post[ReportRequest, Unit]("/reports", req),
                onSuccess = { _ =>
                  close()
                  // 完了モーダルを表示
                  Modal.show(
                    "報告完了",
                    "報告を送信しました。ご協力ありがとうございます。",
                    ModalType.Info
                  )
                },
                customErrorHandler = Some({
                  case e: ApiError if e.message.contains("既にこのコンテンツを報告済みです") =>
                    close() // まずモーダルを閉じる
                    Modal.show("報告済み", "このコンテンツは既に報告されています。", ModalType.Info)
                  case other =>
                    ErrorHandler.handleError(other)
                    isSubmitting.set(false) // 失敗したらボタンを再度有効化
                })
              )
            }
          ),
          button(
            tpe := "button",
            cls := "w-full py-3 text-gray-400 dark:text-gray-500 font-bold text-sm",
            "キャンセル",
            disabled <-- isSubmitting.signal,
            onClick --> { _ => close() }
          )
        )
      )
    )
  }
}
