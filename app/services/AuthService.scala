package services

import javax.inject.*
import repositories.{UserRepository, LoginLogModelRepository}
import models.db.{LoginLogModel, User}
import org.mindrot.jbcrypt.BCrypt
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthService @Inject() (
    userRepo: UserRepository,
    loginLogRepo: LoginLogModelRepository
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  /** ユーザー認証を行い、成功すればUserオブジェクトを返す
    */
  def authenticate(userId: String, rawPassword: String): Future[Either[String, User]] = {
    userRepo.findById(userId).flatMap {
      case Some(user) if BCrypt.checkpw(rawPassword, user.userPassHash) =>
        // ログイン成功時にLogin_Logに記録
        val loginLog = LoginLogModel(
          userId = userId,
          loginDate = System.currentTimeMillis(),
          logoutDate = None
        )
        loginLogRepo.create(loginLog).map(_ => Right(user))
      case Some(_) =>
        Future.successful(Left("ユーザーIDまたはパスワードが正しくありません"))
      case None =>
        Future.successful(Left("ユーザーIDまたはパスワードが正しくありません"))
    }
  }

  // 登録時のイメージ
  def signUp(userId: String, rawPassword: String, userName: String): Future[Either[String, User]] = {
    // 0. ユーザー名のバリデーション
    val trimmedName = utils.Validation.trimAll(userName)
    if (!utils.Validation.notBlank(userId) || userId.length > 50) {
      return Future.successful(Left("ユーザーIDは1文字以上50文字以内で入力してください"))
    } else if (!utils.Validation.notBlank(userName) || trimmedName.length > 30) {
      return Future.successful(Left("ユーザー名に空白のみは使用できません"))
    } else if (!utils.Validation.isValidPassword(rawPassword)) {
      return Future.successful(Left("パスワードは8文字以上、UTF-8で72バイト以内にしてください"))
    }

    // 1. 同一IDのユーザーがいないかチェック
    userRepo.findById(userId).flatMap {
      case Some(_) =>
        Future.successful(Left("このユーザーIDは既に使用されています"))
      case None =>
        // 2. displayIdを生成
        val displayId = utils.DisplayIdGenerator.generate()

        // 3. パスワードをハッシュ化
        val hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt())
        val newUser = User(userId, displayId, trimmedName, hashedPassword, "user", Some(System.currentTimeMillis()))

        // 4. DB保存
        userRepo.save(newUser).map(Right(_))
    }
  }

  /** ログアウト処理：最新のログイン記録にログアウト時刻を記録
    */
  def logout(userId: String): Future[Unit] = {
    // 最新のログイン記録（logoutDateがNullのもの）を取得して更新
    userRepo.incrementTokenVersion(userId).flatMap { _ => loginLogRepo.findLatestLoginByUserId(userId) }.flatMap {
      case Some(loginLog) if loginLog.logoutDate.isEmpty =>
        val updatedLog = loginLog.copy(logoutDate = Some(System.currentTimeMillis()))
        loginLogRepo.update(updatedLog).map(_ => ())
      case _ =>
        // ログイン記録がない場合や、既にログアウト済みの場合は何もしない
        Future.successful(())
    }
  }
}
