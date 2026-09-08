package repositories
import models.db.TagModel
import models.db.TableQueries

import models.db.TableQueries.tags
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class TagModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[TagModel]] = {
    db.run(tags.result)
  }

  // LIKE句の特殊文字をエスケープする
  private def escapeLikePattern(str: String): String = {
    str.flatMap {
      case '\\' => "\\\\"
      case '%'  => "\\%"
      case '_'  => "\\_"
      case c    => c.toString
    }
  }

  def findByKeyword(keyword: String, number: Option[Int] = None): Future[Seq[TagModel]] = {
    val trimmed = keyword.trim

    if (trimmed.isEmpty || trimmed.length > 50) {
      Future.successful(Nil)
    } else {
      val escaped = escapeLikePattern(trimmed)
      val pattern = s"%$escaped%"
      val limit = number.getOrElse(10).min(100)

      db.run(
        tags
          .filter(_.tagName.toLowerCase like s"%${escaped}%".toLowerCase)
          .take(limit)
          .result
      )
    }
  }

  def findByPartialName(q: String): Future[Seq[TagModel]] = {
    // 入力長の制限
    if (q.length > 50) {
      return Future.successful(Nil)
    }

    // tagName で部分一致検索
    val escaped = escapeLikePattern(q)
    val query = TableQueries.tags
      .filter(_.tagName.toLowerCase like s"%${escaped.toLowerCase}%")
      .take(10)
      .result

    db.run(query)
  }

  /** IDでデータを1つ取得する */
  def findById(tagId: Option[Long]): Future[Option[TagModel]] = {
    db.run(tags.filter(_.tagId === tagId).result.headOption)
  }

  /** IDでデータを1つ取得する (Long版) */
  def findById(tagId: Long): Future[Option[TagModel]] = {
    db.run(tags.filter(_.tagId === tagId).result.headOption)
  }

  def findByName(name: String): Future[Seq[TagModel]] = {
    db.run(tags.filter(_.tagName === name).result)
  }

  /** データを新規登録する */
  def create(tag: TagModel): Future[Long] = {
    db.run(tags returning tags.map(_.tagId) += tag)
  }

  /** データ情報を更新する */
  def update(tag: TagModel): Future[Int] = {
    db.run(tags.filter(_.tagId === tag.tagId).update(tag))
  }

  /** データを削除する */
  def delete(tagId: Option[Long]): Future[Int] = {
    db.run(tags.filter(_.tagId === tagId).delete)
  }

}
