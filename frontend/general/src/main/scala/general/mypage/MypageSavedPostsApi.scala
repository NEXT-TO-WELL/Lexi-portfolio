package general.mypage

import models.{FavoriteToggleRequest, FavoriteToggleResponse, MyPageExampleViewValue, MyPageMeaningViewValue}
import scala.concurrent.Future
import util.ApiClient

object MypageSavedPostsApi {
  def fetchFavoriteMeanings(): Future[Seq[MyPageMeaningViewValue]] =
    ApiClient.get[Seq[MyPageMeaningViewValue]]("/user/favorites/meanings")

  def fetchFavoriteExamples(): Future[Seq[MyPageExampleViewValue]] =
    ApiClient.get[Seq[MyPageExampleViewValue]]("/user/favorites/examples")

  def fetchReactedMeanings(): Future[Seq[MyPageMeaningViewValue]] =
    ApiClient.get[Seq[MyPageMeaningViewValue]]("/user/reactions/meanings")

  def fetchReactedExamples(): Future[Seq[MyPageExampleViewValue]] =
    ApiClient.get[Seq[MyPageExampleViewValue]]("/user/reactions/examples")

  def toggleFavorite(contentTypeId: String, contentId: Long): Future[FavoriteToggleResponse] =
    ApiClient.post[FavoriteToggleRequest, FavoriteToggleResponse](
      "/user/favorites/toggle",
      FavoriteToggleRequest(contentTypeId, contentId)
    )
}
