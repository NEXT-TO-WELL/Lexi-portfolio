package models

import upickle.default.ReadWriter

case class DeletedUserModel(
    deletedId: Option[Long],
    userId: String,
    userName: String,
    deleteDate: Long,
    deleteReason: String
) derives ReadWriter
case class LoginLogModel(userId: String, loginDate: Long, logoutDate: Option[Long]) derives ReadWriter
