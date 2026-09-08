package controllers

import javax.inject.*
import play.api.mvc.*
import upickle.default.*
import scala.concurrent.{ExecutionContext, Future}
import models.*
import services.ReportService
import actions.{AdminAuthenticatedAction, AuthenticatedAction}
import shared.api.ErrorResponse

case class ReportRequest(
    reportTypeId: String,
    contentTypeId: String,
    contentId: Long
) derives ReadWriter

case class UpdateStatusRequest(
    reportStatusId: String
) derives ReadWriter

@Singleton
class ReportController @Inject() (
    val controllerComponents: ControllerComponents,
    reportService: ReportService,
    authenticatedAction: AuthenticatedAction,
    adminAuthenticatedAction: AdminAuthenticatedAction
)(implicit ec: ExecutionContext)
    extends BaseController {

  def createReport: Action[String] = authenticatedAction.async(parse.tolerantText) { implicit request =>
    scala.util.Try(read[ReportRequest](request.body)) match {
      case scala.util.Success(req) =>
        reportService
          .createReport(
            request.userId,
            req.reportTypeId,
            req.contentTypeId,
            req.contentId
          )
          .map {
            case Right(report) => Created(write(report)).as("application/json;charset=utf-8")
            case Left(err)     =>
              err.code match {
                case "400" => BadRequest(write(err)).as("application/json;charset=utf-8")
                case _     => InternalServerError(write(err)).as("application/json;charset=utf-8")
              }
          }
      case scala.util.Failure(ex) =>
        play.api.Logger(getClass).debug(s"Report creation JSON parse error: ${ex.getMessage}")
        Future.successful(BadRequest(write(ErrorResponse("400", "不正なリクエスト形式です"))).as("application/json;charset=utf-8"))
    }
  }

  // 管理者用：報告一覧取得
  def getAllReports(limit: Option[Int], offset: Option[Int]): Action[AnyContent] = adminAuthenticatedAction.async {
    implicit request =>
      reportService
        .getAllReportViews(utils.Validation.boundedLimit(limit, 100), utils.Validation.boundedOffset(offset))
        .map { reports =>
          Ok(write(reports)).as("application/json;charset=utf-8")
        }
  }

  // 管理者用：報告ステータス更新
  def updateReportStatus(reportId: Long): Action[String] = adminAuthenticatedAction.async(parse.tolerantText) {
    implicit request =>
      scala.util.Try(read[UpdateStatusRequest](request.body)) match {
        case scala.util.Success(req) =>
          reportService.updateReportStatus(reportId, req.reportStatusId).map {
            case Right(_)  => Ok(write(Map("success" -> true))).as("application/json;charset=utf-8")
            case Left(err) =>
              err.code match {
                case "404" => NotFound(write(err)).as("application/json;charset=utf-8")
                case _     => BadRequest(write(err)).as("application/json;charset=utf-8")
              }
          }
        case scala.util.Failure(ex) =>
          play.api.Logger(getClass).debug(s"Report status update JSON parse error: ${ex.getMessage}")
          Future.successful(
            BadRequest(write(ErrorResponse("400", "不正なリクエスト形式です"))).as("application/json;charset=utf-8")
          )
      }
  }
}
