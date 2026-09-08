package general.mypage

import models.*
import scala.concurrent.Future
import shared.api.{ExampleViewValue, MeaningViewValue}
import util.ApiClient

object MyPostsApi {
  def fetchWords(): Future[Seq[MyPageWordTagViewValue]] =
    ApiClient.get[Seq[MyPageWordTagViewValue]]("/user/words")

  def fetchMeanings(): Future[Seq[MyPageMeaningViewValue]] =
    ApiClient.get[Seq[MyPageMeaningViewValue]]("/user/meanings")

  def fetchExamples(): Future[Seq[MyPageExampleViewValue]] =
    ApiClient.get[Seq[MyPageExampleViewValue]]("/user/examples")

  def fetchThreads(): Future[Seq[ThreadViewValue]] =
    ApiClient.get[Seq[ThreadViewValue]]("/user/threads")

  def fetchComments(): Future[Seq[CommentViewValue]] =
    ApiClient.get[Seq[CommentViewValue]]("/user/comments")

  def deleteMeaning(meaningId: Long): Future[Unit] =
    ApiClient.delete(s"/words/meanings/$meaningId")

  def deleteExample(exampleId: Long): Future[Unit] =
    ApiClient.delete(s"/words/examples/$exampleId")

  def deleteThread(threadId: Long): Future[Unit] =
    ApiClient.delete(s"/threads/$threadId")

  def deleteComment(commentId: Long): Future[Unit] =
    ApiClient.delete(s"/comments/$commentId")

  def updateMeaning(meaningId: Long, request: MeaningPostRequest): Future[MeaningViewValue] =
    ApiClient.put[MeaningPostRequest, MeaningViewValue](s"/words/meanings/$meaningId", request)

  def updateExample(exampleId: Long, request: ExamplePostRequest): Future[ExampleViewValue] =
    ApiClient.put[ExamplePostRequest, ExampleViewValue](s"/words/examples/$exampleId", request)

  def updateThread(threadId: Long, request: ThreadUpdateRequest): Future[ThreadViewValue] =
    ApiClient.put[ThreadUpdateRequest, ThreadViewValue](s"/threads/$threadId", request)

  def updateComment(commentId: Long, request: CommentUpdateByTextRequest): Future[CommentViewValue] =
    ApiClient.put[CommentUpdateByTextRequest, CommentViewValue](s"/comments/$commentId", request)
}
