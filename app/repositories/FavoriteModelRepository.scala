package repositories
import models.db.FavoriteModel
import models.db.{TagModel, Word}
import models.db.User

import models.*
import models.db.TableQueries.*
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.*
import scala.concurrent.{ExecutionContext, Future} // TableQuery のインポート

@Singleton
class FavoriteModelRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  /** 全データを取得する */
  def listAll(): Future[Seq[FavoriteModel]] = {
    db.run(favorites.result)
  }

  // FavoriteModelRepository.scala

  // 意味のお気に入り取得
  def findFavoriteMeaningsByUserId(
      userId: String
  ): Future[Seq[(FavoriteModel, MeaningModel, Option[User], Word, TagModel)]] = {
    val query = favorites
      .filter(f => f.userId === userId && f.contentTypeId === "1")
      .join(meanings)
      .on(_.contentId === _.meaningId)
      .joinLeft(users)
      .on(_._2.userId === _.userId)
      .join(words)
      .on(_._1._2.wordId === _.wordId)
      .join(tags)
      .on(_._1._1._2.tagId === _.tagId)
      .map { case ((((f, m), u), w), t) => (f, m, u, w, t) } // カッコの数を厳密に合わせました

    db.run(query.result)
  }
  // 例文のお気に入り取得
  def findFavoriteExamplesByUserId(
      userId: String
  ): Future[Seq[(FavoriteModel, ExampleModel, Option[User], Word, TagModel)]] = {
    val query = favorites
      .filter(f => f.userId === userId && f.contentTypeId === "2")
      .join(examples)
      .on(_.contentId === _.exampleId)
      .joinLeft(users)
      .on(_._2.userId === _.userId)
      .join(words)
      .on(_._1._2.wordId === _.wordId)
      .join(tags)
      .on(_._1._1._2.tagId === _.tagId)
      .map { case ((((f, e), u), w), t) => (f, e, u, w, t) }

    db.run(query.result)
  }

  // FavoriteModelRepository.scala
  def findByContentIds(userId: String, contentTypeId: String, contentIds: Seq[Long]): Future[Seq[FavoriteModel]] = {
    db.run(
      favorites
        .filter(f =>
          f.userId === userId &&
            f.contentTypeId === contentTypeId &&
            f.contentId.inSet(contentIds)
        )
        .result
    )
  }

  def findExisting(userId: String, contentTypeId: String, contentId: Long): Future[Option[FavoriteModel]] = {
    db.run(
      favorites
        .filter(f => f.userId === userId && f.contentTypeId === contentTypeId && f.contentId === contentId)
        .result
        .headOption
    )
  }

  // お気に入りを削除
  def delete(favoriteId: Long): Future[Int] = {
    db.run(favorites.filter(_.favoriteId === favoriteId).delete)
  }

  /** データを新規登録する */
  def create(favorite: FavoriteModel): Future[Int] = {
    db.run(favorites += favorite)
  }

  /** データ情報を更新する */
  def update(hiddenUser: FavoriteModel): Future[Int] = {
    db.run(favorites.filter(_.favoriteId === hiddenUser.favoriteId).update(hiddenUser))
  }

  /** データを削除する */
  def delete(favoriteId: Option[Long]): Future[Int] = {
    db.run(favorites.filter(_.favoriteId === favoriteId).delete)
  }

  def countByUserId(userId: String): Future[Int] = db.run(favorites.filter(_.userId === userId).length.result)
}
