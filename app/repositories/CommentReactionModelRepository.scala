package repositories
import models.db.TableQueries
import models.db.CommentReactionModel

import models.db.TableQueries.commentReactions
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class CommentReactionModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[CommentReactionModel]] = {
    db.run(commentReactions.result)
  }

  /** IDでデータを1つ取得する */
  def findById(commentId: Option[Long]): Future[Option[CommentReactionModel]] = {
    db.run(commentReactions.filter(_.commentId === commentId).result.headOption)
  }

  def findByCommentIds(userId: String, commentIds: Seq[Long]): Future[Seq[CommentReactionModel]] = {
    db.run(
      commentReactions
        .filter(_.userId === userId)
        .filter(_.commentId inSet commentIds)
        .result
    )
  }

  /** データ情報を更新する */
  def update(reaction: CommentReactionModel): Future[Int] = {
    db.run(commentReactions.filter(_.commentReactionId === reaction.commentReactionId).update(reaction))
  }

  def findByUser(userId: String, commentId: Long): Future[Option[CommentReactionModel]] = {
    db.run(
      TableQueries.commentReactions.filter(r => r.userId === userId && r.commentId === commentId).result.headOption
    )
  }

  def deleteByUser(userId: String, commentId: Long): Future[Int] = {
    db.run(TableQueries.commentReactions.filter(r => r.userId === userId && r.commentId === commentId).delete)
  }

  def create(reaction: CommentReactionModel): Future[Long] = {
    db.run(
      (TableQueries.commentReactions returning TableQueries.commentReactions.map(_.commentReactionId)) += reaction
    )
  }

  /** データを削除する */
  def delete(commentId: Option[Long]): Future[Int] = {
    db.run(commentReactions.filter(_.commentId === commentId).delete)
  }
}
