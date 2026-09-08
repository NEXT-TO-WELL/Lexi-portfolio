package general.thread

import models.*
import shared.api.{CommentReactionToggleResponse, ReactionToggleRequest}
import scala.concurrent.Future
import util.ApiClient

object ThreadDetailApi {
  val PageSize = 10

  def fetchComments(
      threadId: Long,
      lastId: Option[Long] = None
  ): Future[Seq[CommentViewValue]] =
    ApiClient.get[Seq[CommentViewValue]](paginatedCommentsEndpoint(threadId, lastId))

  def fetchThread(threadId: Long): Future[ThreadDetailResponse] =
    ApiClient.get[ThreadDetailResponse](s"/comments/$threadId")

  def toggleReaction(request: ReactionToggleRequest): Future[CommentReactionToggleResponse] =
    ApiClient.post[ReactionToggleRequest, CommentReactionToggleResponse](
      "/reactions/commentToggle",
      request
    )

  def createComment(request: CommentPostRequest): Future[CommentViewValue] =
    ApiClient.post[CommentPostRequest, CommentViewValue]("/comments", request)

  def updateThread(threadId: Long, request: ThreadUpdateRequest): Future[ThreadViewValue] =
    ApiClient.put[ThreadUpdateRequest, ThreadViewValue](s"/threads/$threadId", request)

  def deleteThread(threadId: Long): Future[Unit] =
    ApiClient.delete(s"/threads/$threadId")

  def resolveThread(request: ResolveThreadRequest): Future[Unit] =
    ApiClient.post[ResolveThreadRequest, Unit]("/threads/resolve", request)

  def updateComment(
      commentId: Long,
      request: CommentUpdateByTextRequest
  ): Future[CommentViewValue] =
    ApiClient.put[CommentUpdateByTextRequest, CommentViewValue](s"/comments/$commentId", request)

  def deleteComment(commentId: Long): Future[Unit] =
    ApiClient.delete(s"/comments/$commentId")

  private def paginatedCommentsEndpoint(threadId: Long, lastId: Option[Long]): String =
    lastId match {
      case Some(id) => s"/comments/$threadId/paginated?lastId=$id&limit=$PageSize"
      case None     => s"/comments/$threadId/paginated?limit=$PageSize"
    }
}
