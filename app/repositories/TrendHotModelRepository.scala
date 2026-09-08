package repositories
import models.db.{TagModel, Word}
import models.db.TableQueries

import models.*
import models.db.TableQueries.trendHot
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class TrendHotModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[(TrendHotModel, Option[Word], Option[TagModel])]] = {
    val query = trendHot
      .joinLeft(TableQueries.words)
      .on(_.wordId === _.wordId)
      .joinLeft(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .map { case ((m, w), t) => (m, w, t) } // ネストを解消

    db.run(query.result)
  }

  def refreshHotTrends(): Future[Unit] = {
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
    val summaryQuery = TableQueries.comments
      .filter(_.commentDate > oneDayAgo)
      .join(TableQueries.threads)
      .on(_.threadId === _.threadId)
      .groupBy { case (c, t) => (t.wordId, t.tagId) }
      .map { case ((wId, tId), group) => (wId, tId, group.length) }
      .sortBy(_._3.desc)
      .take(5)

    val action = (for {
      results <- summaryQuery.result
      _ <- trendHot.delete
      _ <- trendHot ++= results.zipWithIndex.map { case ((wId, tId, count), index) =>
        TrendHotModel(index + 1, wId, tId, count)
      }
    } yield ()).transactionally

    db.run(action)
  }

  /** データ情報を更新する */
  def update(trendHotData: TrendHotModel): Future[Int] = {
    db.run(trendHot.filter(_.hotRank === trendHotData.hotRank).update(trendHotData))
  }

}
