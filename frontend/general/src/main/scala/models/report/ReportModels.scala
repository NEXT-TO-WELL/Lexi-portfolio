package models

import upickle.default.ReadWriter

case class ReportModel(
    reportId: Option[Long],
    userId: String,
    reportTypeId: String,
    reportStatusId: String,
    contentTypeId: String,
    contentId: Long,
    reportDate: Long
) derives ReadWriter
case class ReportTargetPreview(title: String, previewText: String, url: Option[String], exists: Boolean)
    derives ReadWriter
case class AdminReportView(report: ReportModel, target: ReportTargetPreview) derives ReadWriter
