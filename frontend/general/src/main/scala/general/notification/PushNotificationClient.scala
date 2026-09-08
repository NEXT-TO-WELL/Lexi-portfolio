package general.notification

import org.scalajs.dom
import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import shared.api.{PushPublicConfig, PushSubscriptionDeleteRequest, PushSubscriptionRequest, PushSubscriptionStatus}
import util.ApiClient

object PushNotificationClient {

  private val PushReceivedMessageType = "LEXI_PUSH_RECEIVED"

  sealed trait State
  case object Loading extends State
  case object Available extends State
  case object Enabling extends State
  case object Enabled extends State
  case object Disabling extends State
  case object Denied extends State
  case object Unsupported extends State
  case object InstallRequired extends State
  case object DisabledByServer extends State
  case object Failed extends State

  private given ExecutionContext = ExecutionContext.global

  private var foregroundListenerInstalled = false

  def installForegroundListener(onPushReceived: () => Unit): Unit = {
    if (!foregroundListenerInstalled && isSupported) {
      val handler: js.Function1[js.Dynamic, Unit] = (event: js.Dynamic) => {
        val data = event.selectDynamic("data")
        if (
          data != null &&
          !js.isUndefined(data) &&
          data.selectDynamic("type").asInstanceOf[String] == PushReceivedMessageType
        ) {
          onPushReceived()
        }
      }
      js.Dynamic.global.navigator.serviceWorker.applyDynamic("addEventListener")("message", handler)
      foregroundListenerInstalled = true
    }
  }

  def loadState(): Future[State] =
    ApiClient
      .get[PushPublicConfig]("/push/config")
      .flatMap { config =>
        if (!config.enabled || config.publicKey.isEmpty) Future.successful(DisabledByServer)
        else if (!isSupported) Future.successful(Unsupported)
        else if (requiresIosInstallation) Future.successful(InstallRequired)
        else
          permission match {
            case "denied"  => Future.successful(Denied)
            case "granted" => existingSubscription().flatMap(reconcileExisting)
            case _         => Future.successful(Available)
          }
      }
      .recover(_ => Failed)

  def enable(): Future[State] =
    ApiClient
      .get[PushPublicConfig]("/push/config")
      .flatMap { config =>
        config.publicKey match {
          case None                               => Future.successful(DisabledByServer)
          case Some(_) if !isSupported            => Future.successful(Unsupported)
          case Some(_) if requiresIosInstallation => Future.successful(InstallRequired)
          case Some(publicKey)                    =>
            requestPermission().flatMap {
              case "granted" =>
                registration().flatMap { registration =>
                  val pushManager = registration.selectDynamic("pushManager")
                  pushManager
                    .applyDynamic("getSubscription")()
                    .asInstanceOf[js.Promise[js.Dynamic]]
                    .toFuture
                    .flatMap { current =>
                      if (current == null || js.isUndefined(current)) {
                        pushManager
                          .applyDynamic("subscribe")(
                            js.Dynamic.literal(
                              userVisibleOnly = true,
                              applicationServerKey = urlBase64ToUint8Array(publicKey)
                            )
                          )
                          .asInstanceOf[js.Promise[js.Dynamic]]
                          .toFuture
                      } else Future.successful(current)
                    }
                    .flatMap(saveSubscription)
                    .map(_ => Enabled)
                }
              case "denied" => Future.successful(Denied)
              case _        => Future.successful(Available)
            }
        }
      }
      .recover(_ => Failed)

  def disable(): Future[State] =
    if (!isSupported) Future.successful(Unsupported)
    else
      existingSubscription()
        .flatMap {
          case None               => Future.successful(Available)
          case Some(subscription) =>
            val endpoint = subscription.selectDynamic("endpoint").asInstanceOf[String]
            ApiClient
              .deleteWithBody[PushSubscriptionDeleteRequest, PushSubscriptionStatus](
                "/push/subscriptions",
                PushSubscriptionDeleteRequest(endpoint)
              )
              .recover(_ => PushSubscriptionStatus(active = false))
              .flatMap(_ => unsubscribeBrowser(subscription))
              .map(_ => Available)
        }
        .recover(_ => Failed)

  def disableForLogout(): Future[Unit] =
    if (!isSupported) Future.successful(())
    else
      existingSubscription()
        .flatMap {
          case None               => Future.successful(())
          case Some(subscription) =>
            val endpoint = subscription.selectDynamic("endpoint").asInstanceOf[String]
            ApiClient
              .deleteWithBody[PushSubscriptionDeleteRequest, PushSubscriptionStatus](
                "/push/subscriptions",
                PushSubscriptionDeleteRequest(endpoint)
              )
              .recover(_ => PushSubscriptionStatus(active = false))
              .flatMap(_ => unsubscribeBrowser(subscription))
        }
        .recover(_ => ())

  def markLaunchNotificationRead(): Unit = {
    val params = new dom.URLSearchParams(dom.window.location.search)
    Option(params.get("pushNotificationId")).flatMap(_.toLongOption).foreach { id =>
      ApiClient.post[ujson.Obj, ujson.Value](s"/notifications/$id/read", ujson.Obj()).recover(_ => ujson.Null)
      params.delete("pushNotificationId")
      val query = params.toString
      val cleanUrl = dom.window.location.pathname + (if (query.nonEmpty) s"?$query" else "") + dom.window.location.hash
      dom.window.history.replaceState(null, "", cleanUrl)
    }
  }

  private def reconcileExisting(subscription: Option[js.Dynamic]): Future[State] =
    subscription match {
      case Some(value) => saveSubscription(value).map(_ => Enabled)
      case None        => Future.successful(Available)
    }

  private def saveSubscription(subscription: js.Dynamic): Future[PushSubscriptionStatus] = {
    val json = subscription.applyDynamic("toJSON")()
    val keys = json.selectDynamic("keys")
    ApiClient.post[PushSubscriptionRequest, PushSubscriptionStatus](
      "/push/subscriptions",
      PushSubscriptionRequest(
        endpoint = json.selectDynamic("endpoint").asInstanceOf[String],
        p256dh = keys.selectDynamic("p256dh").asInstanceOf[String],
        auth = keys.selectDynamic("auth").asInstanceOf[String],
        userAgent = Option(dom.window.navigator.userAgent)
      )
    )
  }

  private def existingSubscription(): Future[Option[js.Dynamic]] =
    registration().flatMap { registration =>
      registration
        .selectDynamic("pushManager")
        .applyDynamic("getSubscription")()
        .asInstanceOf[js.Promise[js.Dynamic]]
        .toFuture
        .map(value => if (value == null || js.isUndefined(value)) None else Some(value))
    }

  private def registration(): Future[js.Dynamic] =
    js.Dynamic.global.navigator.serviceWorker
      .applyDynamic("register")("/service-worker.js")
      .asInstanceOf[js.Promise[js.Dynamic]]
      .toFuture

  private def requestPermission(): Future[String] = {
    val notification = js.Dynamic.global.Notification
    if (notification.selectDynamic("permission").asInstanceOf[String] == "granted") Future.successful("granted")
    else
      notification
        .applyDynamic("requestPermission")()
        .asInstanceOf[js.Promise[String]]
        .toFuture
  }

  private def unsubscribeBrowser(subscription: js.Dynamic): Future[Unit] =
    subscription
      .applyDynamic("unsubscribe")()
      .asInstanceOf[js.Promise[Boolean]]
      .toFuture
      .map(_ => ())

  private def permission: String =
    js.Dynamic.global.Notification.selectDynamic("permission").asInstanceOf[String]

  private def isSupported: Boolean = {
    !js.isUndefined(js.Dynamic.global.Notification) &&
    !js.isUndefined(js.Dynamic.global.PushManager) &&
    !js.isUndefined(js.Dynamic.global.navigator.selectDynamic("serviceWorker"))
  }

  private def requiresIosInstallation: Boolean = {
    val userAgent = dom.window.navigator.userAgent
    val isIos = userAgent.matches(".*(iPhone|iPad|iPod).*")
    val standalone = js.Dynamic.global.window.navigator.selectDynamic("standalone")
    val isStandalone =
      dom.window.matchMedia("(display-mode: standalone)").matches ||
        (!js.isUndefined(standalone) && standalone.asInstanceOf[Boolean])
    isIos && !isStandalone
  }

  private def urlBase64ToUint8Array(value: String): Uint8Array = {
    val padding = "=" * ((4 - value.length % 4) % 4)
    val raw = dom.window.atob((value + padding).replace('-', '+').replace('_', '/'))
    val bytes = new Uint8Array(raw.length)
    var index = 0
    while (index < raw.length) {
      bytes(index) = raw.charAt(index).toShort
      index += 1
    }
    bytes
  }
}
