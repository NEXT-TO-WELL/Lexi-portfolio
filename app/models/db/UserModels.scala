package models.db

import upickle.default.ReadWriter

case class Role(
    roleId: String,
    roleName: String,
    description: Option[String]
)

case class User(
    userId: String,
    displayId: String,
    userName: String,
    userPassHash: String,
    roleId: String = "user",
    userRegistrationDate: Option[Long] = None,
    tokenVersion: Int = 0
)

case class DeletedUserModel(
    deletedId: Option[Long],
    userId: String,
    userName: String,
    deleteDate: Long,
    deleteReason: String
) derives ReadWriter

case class LoginLogModel(
    userId: String,
    loginDate: Long,
    logoutDate: Option[Long]
) derives ReadWriter
