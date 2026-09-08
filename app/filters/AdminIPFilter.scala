package filters

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import play.api.Configuration
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logger
import org.apache.pekko.stream.Materializer

@Singleton
class AdminIPFilter @Inject() (clientIpResolver: ClientIpResolver)(implicit
    val mat: Materializer,
    ec: ExecutionContext,
    config: Configuration
) extends Filter {

  private val logger = Logger(this.getClass)

  // Read the static list and the deployment environment variable. The latter
  // is comma-separated so multiple production IPs can be configured safely.
  private val allowedIPs: Set[String] = {
    val configured = config.getOptional[Seq[String]]("admin.allowed.ips").toSeq.flatten
    val fromEnvironment = config
      .getOptional[String]("admin.allowed.ips-from-environment")
      .toSeq
      .flatMap(_.split(",").toSeq)
      .map(_.trim)
      .filter(_.nonEmpty)
    (configured ++ fromEnvironment).flatMap(clientIpResolver.canonicalize).toSet
  }

  // IP制限フィルターの有効化フラグ
  private val isFilterEnabled: Boolean = config
    .getOptional[Boolean]("admin.ip.filter.enabled")
    .getOrElse(false)

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    // 管理者ログインエンドポイントのみチェック
    if (requestHeader.path == "/api/admin/signIn") {

      // フィルターが無効な場合はスキップ（開発環境）
      if (!isFilterEnabled) {
        logger.debug("Admin IP filter is disabled - skipping IP check")
        return nextFilter(requestHeader)
      }

      val clientIP = clientIpResolver.resolve(requestHeader)

      if (allowedIPs.isEmpty) {
        // 許可IPが設定されていない場合は警告
        logger.warn("Admin allowed IPs not configured - blocking all admin login attempts")
        Future.successful(
          Results
            .Forbidden(
              Json.obj(
                "message" -> "管理者ログインは許可されたIPアドレスからのみアクセス可能です"
              )
            )
            .as("application/json;charset=utf-8")
        )
      } else if (allowedIPs.contains(clientIP)) {
        // 許可されたIPアドレス
        logger.info(s"Admin login attempt from allowed IP: $clientIP")
        nextFilter(requestHeader)
      } else {
        // 許可されていないIPアドレス
        logger.warn(s"Admin login attempt blocked from unauthorized IP: $clientIP")
        Future.successful(
          Results
            .Forbidden(
              Json.obj(
                "message" -> "このIPアドレスからは管理者ログインできません"
              )
            )
            .as("application/json;charset=utf-8")
        )
      }
    } else {
      // 管理者ログイン以外は素通り
      nextFilter(requestHeader)
    }
  }

}
