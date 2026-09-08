package services

import models.*
import repositories.{
  CommentModelRepository,
  ExampleModelRepository,
  MeaningModelRepository,
  ReportModelRepository,
  ThreadModelRepository
}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger
import shared.api.ErrorResponse

@Singleton
class ReportService @Inject() (
    reportRepo: ReportModelRepository,
    meaningRepo: MeaningModelRepository,
    exampleRepo: ExampleModelRepository,
    threadRepo: ThreadModelRepository,
    commentRepo: CommentModelRepository
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  def createReport(
      userId: String,
      reportTypeId: String,
      contentTypeId: String,
      contentId: Long
  ): Future[Either[ErrorResponse, ReportModel]] = {
    // 既に報告済みかチェック
    reportRepo.findByUserAndContent(userId, contentTypeId, contentId).flatMap {
      case Some(_) =>
        Future.successful(Left(ErrorResponse("既にこのコンテンツを報告済みです", "400")))
      case None =>
        val now = System.currentTimeMillis()
        val report = ReportModel(
          reportId = None,
          userId = userId,
          reportTypeId = reportTypeId,
          reportStatusId = "1", // 未処理
          contentTypeId = contentTypeId,
          contentId = contentId,
          reportDate = now
        )
        reportRepo.create(report).map(created => Right(created))
    }
  }

  def getAllReports(limit: Int = 100, offset: Int = 0): Future[Seq[ReportModel]] = {
    reportRepo.findAll(limit, offset)
  }

  def getAllReportViews(limit: Int = 100, offset: Int = 0): Future[Seq[AdminReportView]] = {
    reportRepo.findAll(limit, offset).flatMap { reports =>
      Future.traverse(reports) { report =>
        resolveTarget(report).map(target => AdminReportView(report, target))
      }
    }
  }

  def updateReportStatus(reportId: Long, newStatusId: String): Future[Either[ErrorResponse, Unit]] = {
    reportRepo.updateStatus(reportId, newStatusId).map { count =>
      if (count > 0) Right(())
      else Left(ErrorResponse("報告が見つかりません", "404"))
    }
  }

  private def resolveTarget(report: ReportModel): Future[ReportTargetPreview] = {
    report.contentTypeId match {
      case "1" =>
        meaningRepo.findById(report.contentId).map {
          case Some(m) =>
            ReportTargetPreview(
              title = s"語釈 #${report.contentId}",
              previewText = m.meaningText,
              url = Some(s"/word/${m.wordId}/tag/${m.tagId}/meaning"),
              exists = !m.isDeleted
            )
          case None => missingTarget("語釈", report.contentId)
        }

      case "2" =>
        exampleRepo.findById(report.contentId).map {
          case Some(e) =>
            ReportTargetPreview(
              title = s"用例 #${report.contentId}",
              previewText = e.exampleText,
              url = Some(s"/word/${e.wordId}/tag/${e.tagId}/example"),
              exists = !e.isDeleted
            )
          case None => missingTarget("用例", report.contentId)
        }

      case "3" =>
        threadRepo.findById(report.contentId).map {
          case Some(t) =>
            ReportTargetPreview(
              title = s"スレッド #${report.contentId}",
              previewText = t.threadTitle,
              url = Some(s"/threads/${t.threadId.getOrElse(report.contentId)}"),
              exists = !t.isDeleted
            )
          case None => missingTarget("スレッド", report.contentId)
        }

      case "4" =>
        commentRepo.findById(Some(report.contentId)).map {
          case Some(c) =>
            ReportTargetPreview(
              title = s"コメント #${report.contentId}",
              previewText = c.commentText,
              url = Some(s"/threads/${c.threadId}#comment-${report.contentId}"),
              exists = !c.isDeleted
            )
          case None => missingTarget("コメント", report.contentId)
        }

      case _ =>
        Future.successful(missingTarget("不明な対象", report.contentId))
    }
  }

  private def missingTarget(label: String, contentId: Long): ReportTargetPreview = {
    ReportTargetPreview(
      title = s"$label #$contentId",
      previewText = "対象が見つかりません。削除済み、または不整合の可能性があります。",
      url = None,
      exists = false
    )
  }
}
