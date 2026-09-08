package general.word

import models.*
import shared.api.{ExampleViewValue, MeaningViewValue}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import util.ApiClient

object WordDetailApi {
  val PageSize = 10

  def fetchHeader(wordId: Long, tagId: Long): Future[WordTagDetailResponse] =
    ApiClient.get[WordTagDetailResponse](s"/words/$wordId/$tagId/details")

  def fetchMeanings(
      wordId: Long,
      tagId: Long,
      lastId: Option[Long] = None
  ): Future[Seq[MeaningViewValue]] =
    ApiClient.get[Seq[MeaningViewValue]](
      paginatedEndpoint(s"/words/$wordId/$tagId/meanings/paginated", lastId)
    )

  def fetchAllMeanings(wordId: Long, tagId: Long): Future[Seq[MeaningViewValue]] =
    ApiClient.get[Seq[MeaningViewValue]](s"/words/$wordId/$tagId/meanings")

  def fetchExamples(
      wordId: Long,
      tagId: Long,
      lastId: Option[Long] = None
  ): Future[Seq[ExampleViewValue]] =
    ApiClient.get[Seq[ExampleViewValue]](
      paginatedEndpoint(s"/words/$wordId/$tagId/examples/paginated", lastId)
    )

  def fetchAllExamples(wordId: Long, tagId: Long): Future[Seq[ExampleViewValue]] =
    ApiClient.get[Seq[ExampleViewValue]](s"/words/$wordId/$tagId/examples")

  def fetchThreads(
      wordId: Long,
      tagId: Long,
      lastId: Option[Long] = None
  ): Future[Seq[ThreadViewValue]] =
    ApiClient.get[Seq[ThreadViewValue]](
      paginatedEndpoint(s"/threads/$wordId/$tagId/threads/paginated", lastId)
    )

  def toggleFollow(wordId: Long, tagId: Long): Future[Boolean] = {
    val payload = s"""{"wordId":$wordId,"tagId":$tagId}"""
    ApiClient
      .postRaw[Map[String, Boolean]]("/user/follows/words/toggle", payload)
      .map(_.getOrElse("isFollowed", false))
  }

  def createMeaning(request: MeaningPostRequest): Future[MeaningViewValue] =
    ApiClient.post[MeaningPostRequest, MeaningViewValue]("/words/meanings", request)

  def createExample(request: ExamplePostRequest): Future[ExampleViewValue] =
    ApiClient.post[ExamplePostRequest, ExampleViewValue]("/words/examples", request)

  def createThread(request: ThreadPostRequest): Future[ThreadViewValue] =
    ApiClient.post[ThreadPostRequest, ThreadViewValue]("/threads", request)

  def deleteMeaning(meaningId: Long): Future[Unit] =
    ApiClient.delete(s"/words/meanings/$meaningId")

  def deleteExample(exampleId: Long): Future[Unit] =
    ApiClient.delete(s"/words/examples/$exampleId")

  def updateMeaning(meaningId: Long, request: MeaningPostRequest): Future[MeaningViewValue] =
    ApiClient.put[MeaningPostRequest, MeaningViewValue](s"/words/meanings/$meaningId", request)

  def updateExample(exampleId: Long, request: ExamplePostRequest): Future[ExampleViewValue] =
    ApiClient.put[ExamplePostRequest, ExampleViewValue](s"/words/examples/$exampleId", request)

  private def paginatedEndpoint(base: String, lastId: Option[Long]): String =
    lastId match {
      case Some(id) => s"$base?lastId=$id&limit=$PageSize"
      case None     => s"$base?limit=$PageSize"
    }
}
