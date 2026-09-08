package repositories
import models.db.ReportType

import models.db.TableQueries.reportTypes
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ReportTypeRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ReportType]] = {
    db.run(reportTypes.result)
  }

  /** IDでデータを1つ取得する */
  def findById(reportTypeId: String): Future[Option[ReportType]] = {
    db.run(reportTypes.filter(_.reportTypeId === reportTypeId).result.headOption)
  }

  /** データを新規登録する */
  def create(reportType: ReportType): Future[Int] = {
    db.run(reportTypes += reportType)
  }

  /** データ情報を更新する */
  def update(reportType: ReportType): Future[Int] = {
    db.run(reportTypes.filter(_.reportTypeId === reportType.reportTypeId).update(reportType))
  }

  /** データを削除する */
  def delete(reportTypeId: String): Future[Int] = {
    db.run(reportTypes.filter(_.reportTypeId === reportTypeId).delete)
  }
}
