package repositories
import models.db.ReportStatus

import models.db.TableQueries.reportStatuses
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ReportStatusRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ReportStatus]] = {
    db.run(reportStatuses.result)
  }

  /** IDでデータを1つ取得する */
  def findById(reportStatusId: String): Future[Option[ReportStatus]] = {
    db.run(reportStatuses.filter(_.reportStatusId === reportStatusId).result.headOption)
  }

  /** データを新規登録する */
  def create(reportStatus: ReportStatus): Future[Int] = {
    db.run(reportStatuses += reportStatus)
  }

  /** データ情報を更新する */
  def update(reportStatus: ReportStatus): Future[Int] = {
    db.run(reportStatuses.filter(_.reportStatusId === reportStatus.reportStatusId).update(reportStatus))
  }

  /** データを削除する */
  def delete(reportStatusId: String): Future[Int] = {
    db.run(reportStatuses.filter(_.reportStatusId === reportStatusId).delete)
  }
}
