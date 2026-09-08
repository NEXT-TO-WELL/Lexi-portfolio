package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories._
import models.db.{CommentReactionModel, ReactionModel}
import play.api.Logger
import shared.api.*

@Singleton
class ReactionService @Inject() (
    reactionRepo: ReactionModelRepository,
    meaningRepo: MeaningModelRepository,
    exampleRepo: ExampleModelRepository,
    commentReactionRepo: CommentReactionModelRepository,
    commentModelRepo: CommentModelRepository,
    notificationService: NotificationService
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  def toggleReaction(req: ReactionToggleRequest, userId: String): Future[ReactionToggleResponse] = {
    reactionRepo.findByUser(userId, req.contentId, req.contentTypeId, req.reactionType).flatMap {
      case Some(_) =>
        for {
          _ <- reactionRepo.deleteByUser(userId, req.contentId, req.contentTypeId, req.reactionType)
          latestCount <- updateTargetCount(req.contentId, req.contentTypeId, req.reactionType, -1)
        } yield ReactionToggleResponse(active = false, count = latestCount)
      case None =>
        val model = ReactionModel(None, userId, req.contentTypeId, req.contentId, System.currentTimeMillis())
        for {
          reactionId <- reactionRepo.create(model, req.reactionType)
          latestCount <- updateTargetCount(req.contentId, req.contentTypeId, req.reactionType, 1)
          _ <- notificationService.createReactionNotification(
            req.contentTypeId,
            req.contentId,
            reactionId,
            userId
          )
        } yield ReactionToggleResponse(active = true, count = latestCount)
    }
  }

  // ReactionService.scala
  def toggleCommentReaction(req: ReactionToggleRequest, userId: String): Future[CommentReactionToggleResponse] = {
    commentReactionRepo.findByUser(userId, req.contentId).flatMap {
      case Some(existing) =>
        val isRequestingGood = req.reactionType == "good"

        if (existing.isGood == isRequestingGood) {
          // 同じリアクションの場合は「解除」
          for {
            _ <- commentReactionRepo.deleteByUser(userId, req.contentId)
            (gCount, bCount) <- commentModelRepo.updateAndGetCounts(req.contentId, req.reactionType, -1)
          } yield CommentReactionToggleResponse(active = false, goodCount = gCount, badCount = bCount)
        } else {
          // 違うリアクションの場合は「切り替え」
          val oldType = if (existing.isGood) "good" else "bad"
          val updatedModel = existing.copy(isGood = isRequestingGood, lastModified = System.currentTimeMillis())

          for {
            _ <- commentReactionRepo.update(updatedModel)
            _ <- commentModelRepo.updateReactionCount(req.contentId, oldType, -1)
            (gCount, bCount) <- commentModelRepo.updateAndGetCounts(req.contentId, req.reactionType, 1)
          } yield CommentReactionToggleResponse(active = true, goodCount = gCount, badCount = bCount)
        }

      case None =>
        // 新規リアクション
        val now = System.currentTimeMillis()
        val model = CommentReactionModel(None, Some(userId), req.contentId, req.reactionType == "good", now, now)
        for {
          reactionId <- commentReactionRepo.create(model)
          (gCount, bCount) <- commentModelRepo.updateAndGetCounts(req.contentId, req.reactionType, 1)
          _ <- notificationService.createReactionNotification("4", req.contentId, reactionId, userId)
        } yield CommentReactionToggleResponse(active = true, goodCount = gCount, badCount = bCount)
    }
  }

  private def updateTargetCount(
      contentId: Long,
      contentTypeId: String,
      reactionType: String,
      delta: Int
  ): Future[Int] = {
    contentTypeId match {
      case "1" => meaningRepo.updateReactionCount(contentId, reactionType, delta) //
      case "2" => exampleRepo.updateReactionCount(contentId, reactionType, delta) //
      case _   => Future.successful(0)
    }
  }
}
