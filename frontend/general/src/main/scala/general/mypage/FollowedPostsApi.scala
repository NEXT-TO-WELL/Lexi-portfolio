package general.mypage

import models.{TagFollowsResponse, WordFollowsResponse}
import scala.concurrent.Future
import util.ApiClient

object FollowedPostsApi {
  def fetchTags(): Future[TagFollowsResponse] =
    ApiClient.get[TagFollowsResponse]("/user/follows/tags")

  def fetchWords(): Future[WordFollowsResponse] =
    ApiClient.get[WordFollowsResponse]("/user/follows/words")

  def followTag(tagId: Long): Future[Unit] =
    ApiClient.postRawIgnore("/user/follows/tags", s"""{"tagId": $tagId}""")

  def unfollowTag(followId: Long): Future[Unit] =
    ApiClient.delete(s"/user/follows/$followId")

  def toggleWordFollow(wordId: Long, tagId: Long): Future[Map[String, Boolean]] =
    ApiClient.postRaw[Map[String, Boolean]](
      "/user/follows/words/toggle",
      s"""{"wordId": $wordId, "tagId": $tagId}"""
    )
}
