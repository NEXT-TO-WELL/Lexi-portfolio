package repositories

import models.db.{PushSubscriptionModel, TableQueries}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PushSubscriptionRepository @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  def saveForUser(
      userId: String,
      endpoint: String,
      p256dhKey: String,
      authKey: String,
      userAgent: Option[String]
  ): Future[PushSubscriptionModel] = {
    val now = System.currentTimeMillis()
    val hash = endpointHash(endpoint)
    val lookup = TableQueries.pushSubscriptions.filter(_.endpointHash === hash)

    val action = lookup.result.headOption.flatMap {
      case Some(existing) =>
        val updated = existing.copy(
          userId = userId,
          endpoint = endpoint,
          p256dhKey = p256dhKey,
          authKey = authKey,
          userAgent = userAgent,
          lastModified = now,
          failureCount = 0,
          lastFailureAt = None,
          isDeleted = false,
          deletedAt = None
        )
        lookup.update(updated).map(_ => updated)
      case None =>
        val subscription = PushSubscriptionModel(
          subscriptionId = None,
          userId = userId,
          endpoint = endpoint,
          endpointHash = hash,
          p256dhKey = p256dhKey,
          authKey = authKey,
          userAgent = userAgent,
          createdAt = now,
          lastModified = now,
          failureCount = 0,
          lastFailureAt = None,
          isDeleted = false,
          deletedAt = None
        )
        (TableQueries.pushSubscriptions returning TableQueries.pushSubscriptions.map(_.subscriptionId)).into {
          (row, id) => row.copy(subscriptionId = Some(id))
        } += subscription
    }

    db.run(action.transactionally)
  }

  def findActiveForUser(userId: String): Future[Seq[PushSubscriptionModel]] =
    db.run(TableQueries.pushSubscriptions.filter(s => s.userId === userId && !s.isDeleted).result)

  def deactivateForUser(userId: String, endpoint: String): Future[Boolean] = {
    val now = System.currentTimeMillis()
    val query = TableQueries.pushSubscriptions.filter(s =>
      s.userId === userId && s.endpointHash === endpointHash(endpoint) && !s.isDeleted
    )
    db.run(query.map(s => (s.isDeleted, s.deletedAt, s.lastModified)).update((true, Some(now), now))).map(_ > 0)
  }

  def recordFailure(subscriptionId: Long, permanentlyInvalid: Boolean): Future[Unit] = {
    val now = System.currentTimeMillis()
    val query = TableQueries.pushSubscriptions.filter(_.subscriptionId === subscriptionId)
    db.run(query.result.headOption).flatMap {
      case Some(subscription) =>
        val updated = subscription.copy(
          lastModified = now,
          failureCount = subscription.failureCount + 1,
          lastFailureAt = Some(now),
          isDeleted = subscription.isDeleted || permanentlyInvalid,
          deletedAt = if (permanentlyInvalid) Some(now) else subscription.deletedAt
        )
        db.run(query.update(updated)).map(_ => ())
      case None => Future.successful(())
    }
  }

  private def endpointHash(endpoint: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(endpoint.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString
}
