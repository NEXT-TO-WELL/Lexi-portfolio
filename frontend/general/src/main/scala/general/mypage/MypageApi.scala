package general.mypage

import models.MyProfileResponse
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import util.ApiClient

object MypageApi {
  def fetchProfile(): Future[MyProfileResponse] =
    ApiClient.get[MyProfileResponse]("/user/profile")

  def updateName(name: String): Future[Unit] =
    post("/user/update-name", ujson.Obj("newUserName" -> name))

  def updatePassword(currentPassword: String, newPassword: String): Future[Unit] =
    post(
      "/user/update-password",
      ujson.Obj(
        "currentPassword" -> currentPassword,
        "newPassword" -> newPassword
      )
    )

  def deleteAccount(password: String): Future[Unit] =
    post("/user/delete-account", ujson.Obj("password" -> password))

  private def post(endpoint: String, payload: ujson.Obj): Future[Unit] =
    ApiClient.post[ujson.Obj, ujson.Value](endpoint, payload).map(_ => ())
}
