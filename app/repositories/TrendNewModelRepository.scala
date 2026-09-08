package repositories
import models.db.{TagModel, Word}
import models.db.TableQueries

import models.*
import models.db.TableQueries.trendNew
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class TrendNewModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  // app/repositories/TrendNewModelRepository.scala

  // 戻り値の型をタプルに変更
  def listAll(): Future[Seq[(TrendNewModel, Option[Word], Option[TagModel])]] = {
    val query = trendNew
      .joinLeft(TableQueries.words)
      .on(_.wordId === _.wordId)
      .joinLeft(TableQueries.tags)
      .on(_._1.tagId === _.tagId)
      .map { case ((m, w), t) => (m, w, t) }

    db.run(query.result)
  }

  def refreshNewTrends(): Future[Unit] = {
    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)

    // 1. 24時間以内に作成された単語を対象にする
    val recentWords = TableQueries.wordTags
      .filter(_.wordCreateDate > oneDayAgo)

    // 2. Good, Helpful, Funnyリアクションを集計
    val goodQuery = TableQueries.reactionGood
      .filter(_.contentTypeId === "1") // 語釈
      .join(TableQueries.meanings)
      .on(_.contentId === _.meaningId)
      .join(recentWords)
      .on { case ((r, m), wt) => m.wordId === wt.wordId && m.tagId === wt.tagId }
      .groupBy { case ((r, m), wt) => (wt.wordId, wt.tagId) }
      .map { case ((wId, tId), group) => (wId, tId, group.length) }

    val helpfulQuery = TableQueries.reactionHelpful
      .filter(_.contentTypeId === "1") // 語釈
      .join(TableQueries.meanings)
      .on(_.contentId === _.meaningId)
      .join(recentWords)
      .on { case ((r, m), wt) => m.wordId === wt.wordId && m.tagId === wt.tagId }
      .groupBy { case ((r, m), wt) => (wt.wordId, wt.tagId) }
      .map { case ((wId, tId), group) => (wId, tId, group.length) }

    val funnyQuery = TableQueries.reactionFunny
      .filter(_.contentTypeId === "1") // 語釈
      .join(TableQueries.meanings)
      .on(_.contentId === _.meaningId)
      .join(recentWords)
      .on { case ((r, m), wt) => m.wordId === wt.wordId && m.tagId === wt.tagId }
      .groupBy { case ((r, m), wt) => (wt.wordId, wt.tagId) }
      .map { case ((wId, tId), group) => (wId, tId, group.length) }

    // 3. 用例のリアクションも集計
    val goodExampleQuery = TableQueries.reactionGood
      .filter(_.contentTypeId === "2") // 用例
      .join(TableQueries.examples)
      .on(_.contentId === _.exampleId)
      .join(recentWords)
      .on { case ((r, e), wt) => e.wordId === wt.wordId && e.tagId === wt.tagId }
      .groupBy { case ((r, e), wt) => (wt.wordId, wt.tagId) }
      .map { case ((wId, tId), group) => (wId, tId, group.length) }

    val helpfulExampleQuery = TableQueries.reactionHelpful
      .filter(_.contentTypeId === "2") // 用例
      .join(TableQueries.examples)
      .on(_.contentId === _.exampleId)
      .join(recentWords)
      .on { case ((r, e), wt) => e.wordId === wt.wordId && e.tagId === wt.tagId }
      .groupBy { case ((r, e), wt) => (wt.wordId, wt.tagId) }
      .map { case ((wId, tId), group) => (wId, tId, group.length) }

    val funnyExampleQuery = TableQueries.reactionFunny
      .filter(_.contentTypeId === "2") // 用例
      .join(TableQueries.examples)
      .on(_.contentId === _.exampleId)
      .join(recentWords)
      .on { case ((r, e), wt) => e.wordId === wt.wordId && e.tagId === wt.tagId }
      .groupBy { case ((r, e), wt) => (wt.wordId, wt.tagId) }
      .map { case ((wId, tId), group) => (wId, tId, group.length) }

    val action = (for {
      // 各クエリの結果を取得
      goodResults <- goodQuery.result
      helpfulResults <- helpfulQuery.result
      funnyResults <- funnyQuery.result
      goodExampleResults <- goodExampleQuery.result
      helpfulExampleResults <- helpfulExampleQuery.result
      funnyExampleResults <- funnyExampleQuery.result

      // 全てのリアクションを合算
      allReactions = (goodResults ++ helpfulResults ++ funnyResults ++
        goodExampleResults ++ helpfulExampleResults ++ funnyExampleResults)
        .groupBy { case (wId, tId, _) => (wId, tId) }
        .map { case ((wId, tId), group) =>
          (wId, tId, group.map(_._3).sum)
        }
        .toSeq
        .sortBy(-_._3)
        .take(5)

      _ <- trendNew.delete
      _ <- trendNew ++= allReactions.zipWithIndex.map { case ((wId, tId, count), index) =>
        TrendNewModel(index + 1, wId, tId, count)
      }
    } yield ()).transactionally

    db.run(action)
  }

  /** データ情報を更新する */
  def update(trendNewData: TrendNewModel): Future[Int] = {
    db.run(trendNew.filter(_.newRank === trendNewData.newRank).update(trendNewData))
  }
}
