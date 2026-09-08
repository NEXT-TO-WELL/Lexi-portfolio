package repositories

import models.db.TableQueries.deletedUsers
import models.db.DeletedUserModel
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class DeletedUserModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[DeletedUserModel]] = {
    db.run(deletedUsers.result)
  }

  /** IDでデータを1つ取得する */
  def findById(deletedId: Option[Long]): Future[Option[DeletedUserModel]] = {
    db.run(deletedUsers.filter(_.deletedId === deletedId).result.headOption)
  }

  /** データを新規登録する */
  def create(deletedUser: DeletedUserModel): Future[Int] = {
    db.run(deletedUsers += deletedUser)
  }

  /** データ情報を更新する */
  def update(deletedUser: DeletedUserModel): Future[Int] = {
    db.run(deletedUsers.filter(_.deletedId === deletedUser.deletedId).update(deletedUser))
  }

  /** データを削除する */
  def delete(deletedId: Option[Long]): Future[Int] = {
    db.run(deletedUsers.filter(_.deletedId === deletedId).delete)
  }
}
