package utils

import play.api.Configuration
import pdi.jwt.JwtAlgorithm

// Singleton オブジェクトにする場合は、使う側から config を渡すか、
// クラスにして Inject するのが一般的です。
object JwtUtil {
  def decode(token: String, config: Configuration): Option[String] = {
    val secretKey = config.get[String]("jwt.secret")
    val issuer = config.get[String]("jwt.issuer")

    pdi.jwt.JwtJson
      .decodeJson(token, secretKey, Seq(JwtAlgorithm.HS256))
      .toOption
      .flatMap { json =>
        if ((json \ "iss").asOpt[String].contains(issuer)) {
          (json \ "userId").asOpt[String]
        } else None
      }
  }
}
