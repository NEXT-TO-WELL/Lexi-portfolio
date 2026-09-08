package repositories

import models.db.TableQueries.loginLogs
import models.db.LoginLogModel
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class LoginLogModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[LoginLogModel]] = {
    db.run(loginLogs.result)
  }

  /** IDでデータを1つ取得する */
//  def findById(userId: String, loginDate: Timestamp): Future[Option[LoginLogModel]] = {
//    db.run(loginLogs.filter(r => r.userId === userId && r.loginDate === loginDate).result.headOption)
//  }

  /** データを新規登録する */
  def create(loginLog: LoginLogModel): Future[Int] = {
    db.run(loginLogs += loginLog)
  }

  /** データ情報を更新する */
  def update(loginLog: LoginLogModel): Future[Int] = {
    db.run(loginLogs.filter(r => r.userId === loginLog.userId && r.loginDate === loginLog.loginDate).update(loginLog))
  }

  /** データを削除する */
//  def delete(userId: String, loginDate: Timestamp): Future[Int] = {
//    db.run(loginLogs.filter(r => r.userId === userId && r.loginDate === loginDate).delete)
//  }

  /** ユーザーの最新ログイン記録を取得 */
  def findLatestLoginByUserId(userId: String): Future[Option[LoginLogModel]] = {
    db.run(
      loginLogs
        .filter(_.userId === userId)
        .sortBy(_.loginDate.desc)
        .result
        .headOption
    )
  }
}
