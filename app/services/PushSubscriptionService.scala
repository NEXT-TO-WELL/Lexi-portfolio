package services

import play.api.Configuration
import repositories.PushSubscriptionRepository
import shared.api.{PushPublicConfig, PushSubscriptionRequest, PushSubscriptionStatus}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PushSubscriptionService @Inject() (
    configuration: Configuration,
    repository: PushSubscriptionRepository
)(implicit ec: ExecutionContext) {

  private val enabled = configuration.getOptional[Boolean]("web-push.enabled").contains(true)
  private val publicKey = configuration.getOptional[String]("web-push.vapid.public-key").filter(_.nonEmpty)
  private val privateKey = configuration.getOptional[String]("web-push.vapid.private-key").filter(_.nonEmpty)
  private val subject = configuration.getOptional[String]("web-push.vapid.subject").filter(_.nonEmpty)

  def publicConfig: PushPublicConfig = {
    val configured = enabled && publicKey.isDefined && privateKey.isDefined && subject.isDefined
    PushPublicConfig(configured, publicKey.filter(_ => configured))
  }

  def subscribe(userId: String, request: PushSubscriptionRequest): Future[PushSubscriptionStatus] = {
    val error =
      if (!publicConfig.enabled) Some("Web Push is disabled")
      else if (!request.endpoint.startsWith("https://")) Some("Push endpoint must use HTTPS")
      else if (request.endpoint.length > 2048) Some("Push endpoint is too long")
      else if (request.p256dh.isEmpty || request.auth.isEmpty) Some("Push subscription keys are required")
      else None

    error match {
      case Some(message) => Future.failed(new IllegalArgumentException(message))
      case None          =>
        repository
          .saveForUser(userId, request.endpoint, request.p256dh, request.auth, request.userAgent.map(_.take(500)))
          .map(_ => PushSubscriptionStatus(active = true))
    }
  }

  def unsubscribe(userId: String, endpoint: String): Future[PushSubscriptionStatus] =
    repository.deactivateForUser(userId, endpoint).map(_ => PushSubscriptionStatus(active = false))
}
