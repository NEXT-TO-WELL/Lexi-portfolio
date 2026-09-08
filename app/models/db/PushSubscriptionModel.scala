package models.db

case class PushSubscriptionModel(
    subscriptionId: Option[Long],
    userId: String,
    endpoint: String,
    endpointHash: String,
    p256dhKey: String,
    authKey: String,
    userAgent: Option[String],
    createdAt: Long,
    lastModified: Long,
    failureCount: Int,
    lastFailureAt: Option[Long],
    isDeleted: Boolean,
    deletedAt: Option[Long]
)
