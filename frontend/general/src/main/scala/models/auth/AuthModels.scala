package models

import upickle.default.ReadWriter

case class Role(
    roleId: String,
    roleName: String,
    description: Option[String]
)

case class User(
    userId: String,
    userName: String,
    userPassHash: String,
    roleId: String = "user"
)

case class AdminUserView(
    userId: String,
    displayId: String,
    userName: String,
    roleId: String,
    userRegistrationDate: Option[Long]
) derives ReadWriter
