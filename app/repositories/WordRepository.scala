package repositories
import models.db.ViewHistoryModel
import models.db.Word
import models.db.TableQueries

import javax.inject.*
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import models.db.TableQueries.words // TableQuery のインポート

@Singleton
class WordRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
    extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[Word]] = {
    db.run(words.result)
  }

  /** wordIdが一致するwordを取得する */
  def findByWordId(wordId: Long): Future[Seq[Word]] = {
    db.run(words.filter(_.wordId === wordId).result)
  }

  def findByMain(main: String): Future[Seq[Word]] = {
    db.run(words.filter(_.wordMain === main).result)
  }

  // LIKE句の特殊文字をエスケープする
  private def escapeLikePattern(input: String): String = {
    input
      .replace("\\", "\\\\") // バックスラッシュを最初にエスケープ
      .replace("%", "\\%") // ワイルドカード%をエスケープ
      .replace("_", "\\_") // ワイルドカード_をエスケープ
  }

  def findByPartialName(q: String): Future[Seq[Word]] = {
    // 入力長の制限（50文字以上は無視）
    if (q.length > 50) {
      return Future.successful(Nil)
    }

    // wordMain（単語名）で部分一致検索
    val escaped = escapeLikePattern(q)
    val query = TableQueries.words
      .filter(_.wordMain.toLowerCase like s"%${escaped.toLowerCase}%")
      .take(10)
      .result
    db.run(query)
  }

  def searchByQuery(query: String): Future[Seq[Word]] = {
    // 入力長の制限
    if (query.length > 50) {
      return Future.successful(Nil)
    }

    val escaped = escapeLikePattern(query)
    db.run(words.filter(_.wordMain.toLowerCase like s"%${escaped.toLowerCase}%").take(10).result)
  }

  def upsertViewHistory(userId: String, wordId: Long, tagId: Long): Future[Int] = {
    val now = System.currentTimeMillis()

    val action = TableQueries.viewHistories.insertOrUpdate(
      ViewHistoryModel(userId, wordId, tagId, now)
    )

    db.run(action)
  }

  /** データを新規登録する */
  def create(word: Word): Future[Long] = {
    db.run(words returning words.map(_.wordId) += word)
  }

  /** データ情報を更新する */
  def update(word: Word): Future[Int] = {
    db.run(words.filter(_.wordId === word.wordId).update(word))
  }

  /** データを削除する */
  def delete(wordId: Option[Long]): Future[Int] = {
    db.run(words.filter(_.wordId === wordId).delete)
  }

}
