package repositories

import models.*
import models.db.TableQueries.hiddenUsers
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class HiddenUserModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[HiddenUserModel]] = {
    db.run(hiddenUsers.result)
  }

  /** IDでデータを1つ取得する */
  def findById(userId: String, targetUserId: String): Future[Option[HiddenUserModel]] = {
    db.run(hiddenUsers.filter(r => r.userId === userId && r.targetUserId === targetUserId).result.headOption)
  }

  /** データを新規登録する */
  def create(hiddenUser: HiddenUserModel): Future[Int] = {
    db.run(hiddenUsers += hiddenUser)
  }

  /** データ情報を更新する */
  def update(hiddenUser: HiddenUserModel): Future[Int] = {
    db.run(
      hiddenUsers
        .filter(r => r.userId === hiddenUser.userId && r.targetUserId === hiddenUser.targetUserId)
        .update(hiddenUser)
    )
  }

  /** データを削除する */
  def delete(userId: String, targetUserId: String): Future[Int] = {
    db.run(hiddenUsers.filter(r => r.userId === userId && r.targetUserId === targetUserId).delete)
  }
}
