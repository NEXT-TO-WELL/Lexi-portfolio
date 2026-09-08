package repositories
import models.db.ThreadQuestionModel

import models.db.TableQueries.threadQuestions
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ThreadQuestionModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[ThreadQuestionModel]] = {
    db.run(threadQuestions.result)
  }

  /** IDでデータを1つ取得する */
  def findByThreadId(threadId: Long): Future[Option[ThreadQuestionModel]] = {
    db.run(threadQuestions.filter(_.threadId === threadId).result.headOption)
  }

  def findQuestionStatus(threadId: Long): Future[Option[ThreadQuestionModel]] = {
    db.run(threadQuestions.filter(_.threadId === threadId).result.headOption)
  }

  /** データを新規登録する */
  def create(threadQuestionModel: ThreadQuestionModel): Future[Int] = {
    db.run(threadQuestions += threadQuestionModel)
  }

  // app/repositories/ThreadQuestionModelRepository.scala

  def insert(question: ThreadQuestionModel): Future[ThreadQuestionModel] = {
    // Slick のテーブル定義変数名が threadQuestions であると仮定しています
    db.run(threadQuestions += question).map(_ => question)
  }

  /** データ情報を更新する */
  def update(threadQuestionModel: ThreadQuestionModel): Future[Int] = {
    db.run(threadQuestions.filter(_.threadId === threadQuestionModel.threadId).update(threadQuestionModel))
  }

  /** データを削除する */
  def delete(threadId: Long): Future[Int] = {
    db.run(threadQuestions.filter(_.threadId === threadId).delete)
  }

  /** ベストアンサーを選出して解決済みに更新する */
  def resolve(threadId: Long, bestCommentId: Long): Future[Int] = {
    val now = System.currentTimeMillis()
    // thread_questions テーブルの対象レコードを更新
    val query = threadQuestions.filter(q => q.threadId === threadId && !q.isDeleted)

    db.run(
      query
        .map(q => (q.resolvedDate, q.bestCommentId))
        .update((Some(now), Some(bestCommentId)))
    )
  }
}
