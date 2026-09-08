package repositories
import models.db.TableQueries

import models.*
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportModelRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  private val db = dbConfig.db
  private val reports = TableQueries.reports

  def create(report: ReportModel): Future[ReportModel] = {
    val insertQuery = (reports returning reports.map(_.reportId)
      into ((report, id) => report.copy(reportId = Some(id)))) += report
    db.run(insertQuery)
  }

  def findByUserAndContent(userId: String, contentTypeId: String, contentId: Long): Future[Option[ReportModel]] = {
    db.run(
      reports
        .filter(r =>
          r.userId === userId &&
            r.contentTypeId === contentTypeId &&
            r.contentId === contentId
        )
        .result
        .headOption
    )
  }

  def findAll(limit: Int = 100, offset: Int = 0): Future[Seq[ReportModel]] = {
    db.run(reports.sortBy(_.reportDate.desc).drop(offset).take(limit).result)
  }

  def updateStatus(reportId: Long, newStatusId: String): Future[Int] = {
    db.run(reports.filter(_.reportId === reportId).map(_.reportStatusId).update(newStatusId))
  }
}
