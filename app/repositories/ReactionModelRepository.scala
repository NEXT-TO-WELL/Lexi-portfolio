package repositories
import models.db.{TagModel, Word}
import models.db.User
import models.db.ReactionModel
import models.db.TableQueries

import models.*
import models.db.TableQueries.{examples, meanings, reactionFunny, reactionGood, reactionHelpful, tags, users, words}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class ReactionModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  // 汎用的な検索・削除・作成メソッド
  // app/repositories/ReactionModelRepository.scala

  // app/repositories/ReactionModelRepository.scala

  def findByUser(
      userId: String,
      contentId: Long,
      contentTypeId: String,
      reactionType: String
  ): Future[Option[ReactionModel]] = {
    reactionType match {
      case "good" =>
        db.run(
          reactionGood
            .filter(r => r.userId === userId && r.contentId === contentId && r.contentTypeId === contentTypeId)
            .result
            .headOption
        )
      case "funny" =>
        db.run(
          reactionFunny
            .filter(r => r.userId === userId && r.contentId === contentId && r.contentTypeId === contentTypeId)
            .result
            .headOption
        )
      case "helpful" =>
        db.run(
          reactionHelpful
            .filter(r => r.userId === userId && r.contentId === contentId && r.contentTypeId === contentTypeId)
            .result
            .headOption
        )
      case _ => Future.successful(None)
    }
  }

  def findByContentIds(
      userId: String,
      contentTypeId: String,
      contentIds: Seq[Long],
      reactionType: String
  ): Future[Seq[ReactionModel]] = {
    // 文字列に応じてクエリを組み立てる。ここで型を明示的に指定する
    val query = reactionType match {
      case "good" =>
        TableQueries.reactionGood.filter(r =>
          r.userId === userId && r.contentTypeId === contentTypeId && r.contentId.inSet(contentIds)
        )
      case "funny" =>
        TableQueries.reactionFunny.filter(r =>
          r.userId === userId && r.contentTypeId === contentTypeId && r.contentId.inSet(contentIds)
        )
      case "helpful" =>
        TableQueries.reactionHelpful.filter(r =>
          r.userId === userId && r.contentTypeId === contentTypeId && r.contentId.inSet(contentIds)
        )
      case _ => throw new IllegalArgumentException(s"Invalid reaction type: $reactionType")
    }

    // 結果を Seq[ReactionModel] として取得
    db.run(query.result)
  }

  def findAllByContentIds(userId: String, contentTypeId: String, contentIds: Seq[Long]): Future[Seq[ReactionModel]] = {
    // 先に Future を定義することで並列実行させる
    val fGood = findByContentIds(userId, contentTypeId, contentIds, "good")
    val fFunny = findByContentIds(userId, contentTypeId, contentIds, "funny")
    val fHelpful = findByContentIds(userId, contentTypeId, contentIds, "helpful")

    // すべての結果が揃うのを待って合体させる
    for {
      goods <- fGood
      funnies <- fFunny
      helpfuls <- fHelpful
    } yield {
      // それぞれのモデルに reactionType を付与して返す必要がある場合
      // (ReactionModel 側に reactionType フィールドがない場合は、ここで補完するか、
      //  戻り値を Seq[(ReactionModel, String)] にするなどの工夫が必要です)
      goods.map(_.copy()) ++ funnies.map(_.copy()) ++ helpfuls.map(_.copy())
    }
  }

  def deleteByUser(userId: String, contentId: Long, contentTypeId: String, reactionType: String): Future[Int] = {
    reactionType match {
      case "good" =>
        db.run(
          reactionGood
            .filter(r => r.userId === userId && r.contentId === contentId && r.contentTypeId === contentTypeId)
            .delete
        )
      case "funny" =>
        db.run(
          reactionFunny
            .filter(r => r.userId === userId && r.contentId === contentId && r.contentTypeId === contentTypeId)
            .delete
        )
      case "helpful" =>
        db.run(
          reactionHelpful
            .filter(r => r.userId === userId && r.contentId === contentId && r.contentTypeId === contentTypeId)
            .delete
        )
      case _ => Future.successful(0)
    }
  }

  def create(reaction: ReactionModel, reactionType: String): Future[Long] = {
    reactionType match {
      case "good"    => db.run((reactionGood returning reactionGood.map(_.id)) += reaction)
      case "funny"   => db.run((reactionFunny returning reactionFunny.map(_.id)) += reaction)
      case "helpful" => db.run((reactionHelpful returning reactionHelpful.map(_.id)) += reaction)
      case _         => Future.failed(new IllegalArgumentException(s"Invalid reaction type: $reactionType"))
    }
  }

  def findMeaningReactionsByUserId(
      userId: String
  ): Future[Seq[(ReactionModel, String, MeaningModel, Option[User], Word, TagModel)]] = {
    def queryFor(reactionType: String) = {
      val reactions = reactionType match {
        case "good"    => reactionGood
        case "helpful" => reactionHelpful
        case "funny"   => reactionFunny
      }

      reactions
        .filter(r => r.userId === userId && r.contentTypeId === "1")
        .join(meanings.filter(!_.isDeleted))
        .on(_.contentId === _.meaningId)
        .joinLeft(users)
        .on(_._2.userId === _.userId)
        .join(words)
        .on(_._1._2.wordId === _.wordId)
        .join(tags)
        .on(_._1._1._2.tagId === _.tagId)
        .map { case ((((r, m), u), w), t) => (r, reactionType, m, u, w, t) }
    }

    db.run((queryFor("good") unionAll queryFor("helpful") unionAll queryFor("funny")).result)
  }

  def findExampleReactionsByUserId(
      userId: String
  ): Future[Seq[(ReactionModel, String, ExampleModel, Option[User], Word, TagModel)]] = {
    def queryFor(reactionType: String) = {
      val reactions = reactionType match {
        case "good"    => reactionGood
        case "helpful" => reactionHelpful
        case "funny"   => reactionFunny
      }

      reactions
        .filter(r => r.userId === userId && r.contentTypeId === "2")
        .join(examples.filter(!_.isDeleted))
        .on(_.contentId === _.exampleId)
        .joinLeft(users)
        .on(_._2.userId === _.userId)
        .join(words)
        .on(_._1._2.wordId === _.wordId)
        .join(tags)
        .on(_._1._1._2.tagId === _.tagId)
        .map { case ((((r, e), u), w), t) => (r, reactionType, e, u, w, t) }
    }

    db.run((queryFor("good") unionAll queryFor("helpful") unionAll queryFor("funny")).result)
  }
}
