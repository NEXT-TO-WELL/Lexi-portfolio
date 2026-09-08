package repositories

import javax.inject._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future} // Case Class (UserModel) のインポート
import models.db.TableQueries.users // TableQuery のインポート
import models.db.User

@Singleton
class UserRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  /** 全ユーザーを取得する */
  def listAll(): Future[Seq[User]] = {
    db.run(users.result)
  }

  /** 管理者画面用：新しいユーザーから取得する */
  def listRecent(limit: Int = 100, offset: Int = 0): Future[Seq[User]] = {
    db.run(users.sortBy(_.userRegistrationDate.desc).drop(offset).take(limit).result)
  }

  /** IDでユーザーを1名取得する */
  def findById(userId: String): Future[Option[User]] = {
    db.run(users.filter(_.userId === userId).result.headOption)
  }

  /** 複数のIDでユーザーを取得する */
  def findByIds(userIds: Seq[String]): Future[Seq[User]] = {
    db.run(users.filter(_.userId.inSet(userIds)).result)
  }

  /** ユーザーを新規登録する */
  def save(user: User): Future[User] = {
    db.run(users += user).map(_ => user)
  }

  /** ユーザー情報を更新する */
  def update(user: User): Future[Int] = {
    db.run(users.filter(_.userId === user.userId).update(user))
  }

  def incrementTokenVersion(userId: String): Future[Int] =
    db.run(sqlu"UPDATE Users SET token_version = token_version + 1 WHERE user_id = $userId")

  def updatePasswordAndIncrementTokenVersion(userId: String, passwordHash: String): Future[Int] =
    db.run(
      sqlu"UPDATE Users SET user_pass_hash = $passwordHash, token_version = token_version + 1 WHERE user_id = $userId"
    )

  /** ユーザーを削除する */
  def delete(userId: String): Future[Int] = {
    db.run(users.filter(_.userId === userId).delete)
  }
}
