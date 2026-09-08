package repositories
import models.db.{TagModel, Word}
import models.db.TableQueries

import models.*
import models.db.TableQueries.trendBuzz
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class TrendBuzzModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[(TrendBuzzModel, Option[Word], Option[TagModel])]] = {
    val query = trendBuzz
      .joinLeft(TableQueries.words)
      .on(_.wordId === _.wordId)
      .joinLeft(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .map { case ((m, w), t) => (m, w, t) }

    db.run(query.result)
  }

  def refreshBuzzTrends(): Future[Unit] = {
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)

    // 1. 24時間以内の閲覧履歴を集計
    val query = TableQueries.viewHistories
      .filter(_.viewDate > oneDayAgo)
      .groupBy(v => (v.wordId, v.tagId))
      .map { case ((wId, tId), group) => (wId, tId, group.length) }
      .sortBy(_._3.desc)
      .take(5)

    // 2. トランザクションとしてまとめる
    val action = (for {
      results <- query.result
      _ <- trendBuzz.delete
      _ <- trendBuzz ++= results.zipWithIndex.map { case ((wId, tId, count), index) =>
        TrendBuzzModel(index + 1, wId, tId, count)
      }
    } yield ()).transactionally

    db.run(action)
  }

  /** IDでデータを1つ取得する */
  def findById(buzzRank: Int): Future[Option[TrendBuzzModel]] = {
    db.run(trendBuzz.filter(_.buzzRank === buzzRank).result.headOption)
  }

  /** データを新規登録する */
  def create(trendBuzzData: TrendBuzzModel): Future[Int] = {
    db.run(trendBuzz += trendBuzzData)
  }

  /** データ情報を更新する */
  def update(trendBuzzData: TrendBuzzModel): Future[Int] = {
    db.run(trendBuzz.filter(_.buzzRank === trendBuzzData.buzzRank).update(trendBuzzData))
  }

  /** データを削除する */
  def delete(buzzRank: Int): Future[Int] = {
    db.run(trendBuzz.filter(_.buzzRank === buzzRank).delete)
  }
}
