package shared.api

import upickle.default.ReadWriter

case class PushPublicConfig(enabled: Boolean, publicKey: Option[String]) derives ReadWriter

case class PushSubscriptionRequest(
    endpoint: String,
    p256dh: String,
    auth: String,
    userAgent: Option[String] = None
) derives ReadWriter

case class PushSubscriptionDeleteRequest(endpoint: String) derives ReadWriter

case class PushSubscriptionStatus(active: Boolean) derives ReadWriter
