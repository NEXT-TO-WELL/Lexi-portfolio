package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories._
import models._

@Singleton
class TrendService @Inject() (
    hotRepo: TrendHotModelRepository,
    newRepo: TrendNewModelRepository,
    buzzRepo: TrendBuzzModelRepository,
    wordTagRepo: WordTagModelRepository
)(implicit ec: ExecutionContext) {
  private val logger = play.api.Logger(getClass)
  def getFullTrends: Future[TrendResponse] = {
    for {
      hotTuple <- hotRepo.listAll() // List[(Model, Option[Word], Option[TagModel])]
      newsTuple <- newRepo.listAll()
      buzzTuple <- buzzRepo.listAll()
      randomTuple <- wordTagRepo.getRandomWordTags(5)
    } yield {
      // 詰め替え処理
      TrendResponse(
        // case (m, w, t) でタプルを受け取る
        hot = hotTuple.map { case (m, w, t) =>
          TrendViewValue(
            m.hotRank,
            m.wordId,
            w.map(_.wordMain).getOrElse("no data"),
            m.tagId,
            t.map(_.tagName).getOrElse("no data"),
            m.commentCount,
            "HOT"
          )
        },
        news = newsTuple.map { case (m, w, t) =>
          TrendViewValue(
            m.newRank,
            m.wordId,
            w.map(_.wordMain).getOrElse("no data"),
            m.tagId,
            t.map(_.tagName).getOrElse("no data"),
            m.goodCount,
            "NEW"
          )
        },
        buzz = buzzTuple.map { case (m, w, t) =>
          TrendViewValue(
            m.buzzRank,
            m.wordId,
            w.map(_.wordMain).getOrElse("no data"),
            m.tagId,
            t.map(_.tagName).getOrElse("no data"),
            m.goodCount,
            "BUZZ"
          )
        },
        random = randomTuple.zipWithIndex.map { case ((wt, w, t), idx) =>
          TrendViewValue(
            idx + 1,
            wt.wordId,
            w.map(_.wordMain).getOrElse("no data"),
            wt.tagId,
            t.map(_.tagName).getOrElse("no data"),
            0,
            "RANDOM"
          )
        }
      )
    }
  }

  def updateAllTrends(): Future[Unit] = {
    val startTime = System.currentTimeMillis()
    logger.info(s"Trend update started at: $startTime")

    val result: Future[Unit] = for {
      _ <- hotRepo.refreshHotTrends()
      _ <- newRepo.refreshNewTrends()
      _ <- buzzRepo.refreshBuzzTrends()
    } yield {
      val duration = System.currentTimeMillis() - startTime
      logger.info(s"Successfully updated all trends in ${duration}ms.")
    }

    // recover でも Unit を返すように明示
    result.recover { case e: Exception =>
      logger.error(s"Failed to update trends: ${e.getMessage}", e)
    }
  }
}
