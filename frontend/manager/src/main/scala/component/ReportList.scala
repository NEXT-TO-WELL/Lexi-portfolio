package component

import com.raquo.laminar.api.L.*
import models.AdminReportView
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global
import util.ApiClient

object ReportList {

  private val reportsVar = Var(Seq.empty[AdminReportView])
  private val isLoading = Var(true)
  private val statusFilterVar = Var("all")
  private val contentTypeFilterVar = Var("all")
  private val reportTypeFilterVar = Var("all")

  // 報告タイプのマッピング
  private def getReportTypeName(typeId: String): String = typeId match {
    case "1" => "スパム"
    case "2" => "不適切なコンテンツ"
    case "3" => "誹謗中傷"
    case "4" => "その他"
    case _   => "不明"
  }

  // コンテンツタイプのマッピング
  private def getContentTypeName(typeId: String): String = typeId match {
    case "1" => "語釈"
    case "2" => "用例"
    case "3" => "スレッド"
    case "4" => "コメント"
    case _   => "不明"
  }

  // ステータスのマッピング
  private def getStatusName(statusId: String): String = statusId match {
    case "1" => "未対応"
    case "2" => "対応中"
    case "3" => "対応済み"
    case "4" => "却下"
    case _   => "不明"
  }

  // ステータスの色
  private def getStatusColor(statusId: String): String = statusId match {
    case "1" => "bg-yellow-100 text-yellow-800"
    case "2" => "bg-blue-100 text-blue-800"
    case "3" => "bg-green-100 text-green-800"
    case "4" => "bg-gray-100 text-gray-800"
    case _   => "bg-gray-100 text-gray-800"
  }

  def apply(): HtmlElement = {
    val filteredReportsSignal = Signal
      .combine(
        reportsVar.signal,
        statusFilterVar.signal,
        contentTypeFilterVar.signal,
        reportTypeFilterVar.signal
      )
      .map { case (reports, statusFilter, contentTypeFilter, reportTypeFilter) =>
        reports.filter { view =>
          val report = view.report
          (statusFilter == "all" || report.reportStatusId == statusFilter) &&
          (contentTypeFilter == "all" || report.contentTypeId == contentTypeFilter) &&
          (reportTypeFilter == "all" || report.reportTypeId == reportTypeFilter)
        }
      }

    div(
      cls := "space-y-4",

      // タイトル
      div(
        cls := "flex justify-between items-center gap-4",
        div(
          cls := "flex items-center gap-3",
          h2(cls := "text-xl font-bold text-gray-900", "報告一覧"),
          child <-- reportsVar.signal.map { reports =>
            val openCount = reports.count(_.report.reportStatusId == "1")
            span(
              cls := "px-2.5 py-1 rounded-full bg-yellow-100 text-yellow-800 text-xs font-bold",
              s"未対応 $openCount 件"
            )
          }
        ),
        button(
          cls := "px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition",
          "再読み込み",
          onClick --> { _ => fetchReports() }
        )
      ),

      renderFilters(),

      // ローディング表示
      child <-- isLoading.signal.map { loading =>
        if (loading) {
          div(
            cls := "py-20 text-center",
            div(cls := "inline-block animate-spin rounded-full h-8 w-8 border-4 border-blue-500 border-t-transparent")
          )
        } else {
          emptyNode
        }
      },

      // 報告リスト
      child <-- filteredReportsSignal.combineWith(isLoading.signal, reportsVar.signal).map {
        case (reports, loading, allReports) =>
          if (!loading) {
            if (allReports.isEmpty) {
              div(
                cls := "bg-white rounded-lg shadow p-8 text-center text-gray-500",
                "報告はありません"
              )
            } else if (reports.isEmpty) {
              div(
                cls := "bg-white rounded-lg shadow p-8 text-center text-gray-500",
                "条件に一致する報告はありません"
              )
            } else {
              div(
                cls := "space-y-3",
                reports.map(renderReportCard)
              )
            }
          } else {
            emptyNode
          }
      },

      onMountCallback { _ =>
        fetchReports()
      }
    )
  }

  private def renderFilters(): HtmlElement = {
    div(
      cls := "bg-white border border-gray-200 rounded-lg p-4 grid grid-cols-1 md:grid-cols-3 gap-3",
      filterSelect(
        labelText = "ステータス",
        valueSignal = statusFilterVar.signal,
        setFilter = statusFilterVar.set,
        optionItems = Seq(
          "all" -> "すべて",
          "1" -> "未対応",
          "2" -> "対応中",
          "3" -> "対応済み",
          "4" -> "却下"
        )
      ),
      filterSelect(
        labelText = "対象種別",
        valueSignal = contentTypeFilterVar.signal,
        setFilter = contentTypeFilterVar.set,
        optionItems = Seq(
          "all" -> "すべて",
          "1" -> "語釈",
          "2" -> "用例",
          "3" -> "スレッド",
          "4" -> "コメント"
        )
      ),
      filterSelect(
        labelText = "報告理由",
        valueSignal = reportTypeFilterVar.signal,
        setFilter = reportTypeFilterVar.set,
        optionItems = Seq(
          "all" -> "すべて",
          "1" -> "スパム",
          "2" -> "不適切なコンテンツ",
          "3" -> "誹謗中傷",
          "4" -> "その他"
        )
      )
    )
  }

  private def filterSelect(
      labelText: String,
      valueSignal: Signal[String],
      setFilter: String => Unit,
      optionItems: Seq[(String, String)]
  ): HtmlElement = {
    label(
      cls := "space-y-1",
      span(cls := "block text-xs font-bold text-gray-500", labelText),
      select(
        cls := "w-full px-3 py-2 rounded-lg border border-gray-200 bg-white text-sm font-medium text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500",
        value <-- valueSignal,
        onChange.mapToValue --> setFilter,
        optionItems.map { case (optionValue, text) =>
          option(value := optionValue, text)
        }
      )
    )
  }

  private def renderReportCard(view: AdminReportView): HtmlElement = {
    val report = view.report
    val target = view.target
    val dateStr = new scala.scalajs.js.Date(report.reportDate.toDouble).toLocaleString()

    div(
      cls := "bg-white rounded-lg shadow-sm border border-gray-200 p-4 hover:shadow-md transition",

      div(
        cls := "flex justify-between items-start gap-4 mb-3",

        // 左側：報告情報
        div(
          cls := "space-y-3 min-w-0 flex-1",
          div(
            cls := "flex items-center space-x-2",
            span(
              cls := s"px-2 py-1 rounded text-xs font-bold ${getStatusColor(report.reportStatusId)}",
              getStatusName(report.reportStatusId)
            ),
            span(
              cls := "px-2 py-1 rounded text-xs font-bold bg-red-100 text-red-800",
              getReportTypeName(report.reportTypeId)
            ),
            span(
              cls := "px-2 py-1 rounded text-xs font-medium bg-gray-100 text-gray-700",
              getContentTypeName(report.contentTypeId)
            )
          ),
          div(
            cls := "text-sm text-gray-600",
            s"報告ID: ${report.reportId.getOrElse("不明")}"
          ),
          div(
            cls := "text-sm text-gray-600",
            s"コンテンツID: ${report.contentId}"
          ),
          div(
            cls := "rounded-lg border border-gray-100 bg-gray-50 p-3 space-y-2",
            div(
              cls := "flex items-center justify-between gap-3",
              div(
                cls := "min-w-0",
                div(
                  cls := "text-xs font-bold text-gray-500",
                  "報告対象"
                ),
                div(
                  cls := "text-sm font-bold text-gray-900 truncate",
                  target.title
                )
              ),
              target.url
                .map { url =>
                  a(
                    cls := "flex-shrink-0 px-3 py-1.5 text-xs font-bold rounded-md bg-white border border-gray-200 text-blue-600 hover:bg-blue-50 transition",
                    href := url,
                    "開く"
                  )
                }
                .getOrElse(
                  span(cls := "flex-shrink-0 px-2 py-1 text-xs font-bold rounded bg-gray-200 text-gray-500", "リンクなし")
                )
            ),
            p(
              cls := "text-sm text-gray-700 leading-relaxed whitespace-pre-wrap break-words",
              truncatePreview(target.previewText)
            ),
            if (!target.exists) {
              div(cls := "text-xs font-bold text-red-600", "対象は削除済み、または見つかりません")
            } else emptyNode
          ),
          div(
            cls := "text-xs text-gray-400",
            s"報告日時: $dateStr"
          ),
          div(
            cls := "text-xs text-gray-400",
            s"報告者: ${report.userId}"
          )
        ),

        // 右側：アクションボタン
        div(
          cls := "flex flex-col space-y-2",
          report.reportId
            .map { id =>
              div(
                cls := "space-y-2",
                if (report.reportStatusId == "1") {
                  button(
                    cls := "px-3 py-1 bg-blue-600 text-white text-xs rounded hover:bg-blue-700 transition whitespace-nowrap",
                    "対応中にする",
                    onClick --> { _ => updateStatus(id, "2") }
                  )
                } else {
                  emptyNode
                },
                if (report.reportStatusId != "3") {
                  button(
                    cls := "px-3 py-1 bg-green-600 text-white text-xs rounded hover:bg-green-700 transition whitespace-nowrap",
                    "対応済みにする",
                    onClick --> { _ => updateStatus(id, "3") }
                  )
                } else {
                  emptyNode
                },
                if (report.reportStatusId != "4") {
                  button(
                    cls := "px-3 py-1 bg-gray-600 text-white text-xs rounded hover:bg-gray-700 transition whitespace-nowrap",
                    "却下する",
                    onClick --> { _ => updateStatus(id, "4") }
                  )
                } else {
                  emptyNode
                }
              )
            }
            .getOrElse(emptyNode)
        )
      )
    )
  }

  private def fetchReports(): Unit = {
    isLoading.set(true)
    val future = ApiClient.get[Seq[AdminReportView]]("/reports/all")
    future.foreach { reports =>
      reportsVar.set(reports)
      isLoading.set(false)
    }
    future.recover { case ex =>
      dom.console.error(s"Failed to fetch reports: ${ex.getMessage}")
      isLoading.set(false)
    }
  }

  private def truncatePreview(text: String): String = {
    val normalized = text.trim
    if (normalized.length <= 180) normalized else normalized.take(180) + "..."
  }

  private def updateStatus(reportId: Long, newStatusId: String): Unit = {
    import upickle.default.*
    case class UpdateReq(reportStatusId: String) derives ReadWriter
    case class UpdateRes(success: Boolean) derives ReadWriter

    val future = ApiClient.put[UpdateReq, UpdateRes](s"/reports/$reportId/status", UpdateReq(newStatusId))
    future.foreach { _ =>
      // 成功したら再読み込み
      fetchReports()
    }
    future.recover { case ex =>
      dom.console.error(s"Failed to update status: ${ex.getMessage}")
    }
  }
}
