package services

import nl.martijndwars.webpush.{Encoding, Notification as WebPushNotification, PushService}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import play.api.{Configuration, Logger}
import repositories.PushSubscriptionRepository
import ujson.Obj

import java.nio.charset.StandardCharsets
import java.security.Security
import javax.inject.{Inject, Singleton}
import org.apache.http.HttpResponse
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try

private[services] trait WebPushSender {
  def send(notification: WebPushNotification): HttpResponse
}

private[services] final class Aes128GcmWebPushSender(pushService: PushService) extends WebPushSender {
  override def send(notification: WebPushNotification): HttpResponse =
    pushService.send(notification, Encoding.AES128GCM)
}

@Singleton
class NotificationDispatcher @Inject() (
    configuration: Configuration,
    repository: PushSubscriptionRepository
)(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass)
  private val enabled = configuration.getOptional[Boolean]("web-push.enabled").contains(true)
  private val publicKey = configuration.getOptional[String]("web-push.vapid.public-key").filter(_.nonEmpty)
  private val privateKey = configuration.getOptional[String]("web-push.vapid.private-key").filter(_.nonEmpty)
  private val subject = configuration.getOptional[String]("web-push.vapid.subject").filter(_.nonEmpty)

  private lazy val pushService: Option[WebPushSender] =
    if (enabled) {
      for {
        publicKeyValue <- publicKey
        privateKeyValue <- privateKey
        subjectValue <- subject
      } yield {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider())
        new Aes128GcmWebPushSender(new PushService(publicKeyValue, privateKeyValue, subjectValue))
      }
    } else None

  def dispatch(
      notificationId: Long,
      userId: String,
      notificationType: String,
      message: String,
      link: String
  ): Future[Unit] = {
    if (notificationType == "SYSTEM" || !enabled) {
      Future.successful(())
    } else {
      Try(pushService).fold(
        error => {
          logger.error("Web Push configuration is invalid; delivery was skipped", error)
          Future.successful(())
        },
        {
          case None         => Future.successful(())
          case Some(sender) => deliver(sender, notificationId, userId, message, link)
        }
      )
    }
  }

  private def deliver(
      sender: WebPushSender,
      notificationId: Long,
      userId: String,
      message: String,
      link: String
  ): Future[Unit] = {
    val payload = Obj(
      "notificationId" -> notificationId,
      "title" -> "Lexiからのお知らせ",
      "body" -> message,
      "url" -> link
    ).render()

    repository.findActiveForUser(userId).flatMap { subscriptions =>
      Future
        .traverse(subscriptions) { subscription =>
          Future {
            blocking {
              val webPushNotification = new WebPushNotification(
                subscription.endpoint,
                subscription.p256dhKey,
                subscription.authKey,
                payload.getBytes(StandardCharsets.UTF_8)
              )
              val response = sender.send(webPushNotification)
              try response.getStatusLine.getStatusCode
              finally
                response match {
                  case closeable: AutoCloseable => closeable.close()
                  case _                        => ()
                }
            }
          }.flatMap { statusCode =>
            if (statusCode >= 200 && statusCode < 300) Future.successful(())
            else {
              val permanentlyInvalid = statusCode == 404 || statusCode == 410
              logger.warn(
                s"Web Push delivery failed for subscription ${subscription.subscriptionId.getOrElse(0L)} " +
                  s"with HTTP $statusCode"
              )
              subscription.subscriptionId match {
                case Some(id) => repository.recordFailure(id, permanentlyInvalid)
                case None     => Future.successful(())
              }
            }
          }.recoverWith { case error =>
            logger.warn(
              s"Web Push delivery failed for subscription ${subscription.subscriptionId.getOrElse(0L)}",
              error
            )
            subscription.subscriptionId match {
              case Some(id) => repository.recordFailure(id, permanentlyInvalid = false)
              case None     => Future.successful(())
            }
          }
        }
        .map(_ => ())
    }
  }
}
