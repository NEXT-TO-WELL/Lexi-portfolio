package filters

import org.apache.pekko.stream.Materializer
import play.api.Configuration
import play.api.mvc._
import play.api.libs.json.Json

import javax.inject.{Inject, Singleton}
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RateLimitFilter @Inject() (configuration: Configuration, clientIpResolver: ClientIpResolver)(implicit
    val mat: Materializer,
    ec: ExecutionContext
) extends Filter {

  private val enabled = configuration.getOptional[Boolean]("rate-limit.enabled").getOrElse(true)

  // IPアドレスごとのリクエスト履歴を保持
  private val requestHistory = TrieMap[String, List[Long]]()

  // 設定
  private val windowSeconds = 60 // 60秒間のウィンドウ
  private val maxRequests = 100 // 60秒間に最大100リクエスト（通常の閲覧には十分な余裕）

  // サジェストエンドポイント用の厳しい制限
  private val suggestWindowSeconds = 10 // 10秒間のウィンドウ
  private val suggestMaxRequests = 5 // 10秒間に最大5リクエスト

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    if (!enabled) return nextFilter(requestHeader)

    // サジェスト関連のエンドポイントかチェック
    val isSuggestEndpoint = requestHeader.path.contains("/suggest") ||
      requestHeader.path.contains("/words/suggest") ||
      requestHeader.path.contains("/suggests/")

    val clientIp = clientIpResolver.resolve(requestHeader)
    val now = System.currentTimeMillis()

    val (window, maxReq) = if (isSuggestEndpoint) {
      (suggestWindowSeconds * 1000L, suggestMaxRequests)
    } else {
      (windowSeconds * 1000L, maxRequests)
    }

    val allowed = requestHistory.synchronized {
      val recentHistory = requestHistory.getOrElse(clientIp, List.empty).filter(timestamp => now - timestamp < window)
      if (recentHistory.length >= maxReq) false
      else {
        requestHistory.update(clientIp, now :: recentHistory)
        true
      }
    }

    if (!allowed) {
      // 制限超過
      Future.successful(
        Results.TooManyRequests(
          Json.obj(
            "error" -> "Rate limit exceeded",
            "message" -> s"最大リクエスト数（${maxReq}回/${window / 1000}秒）を超えました。しばらく待ってから再試行してください。"
          )
        )
      )
    } else {
      // 定期的にクリーンアップ（10分以上古いエントリを削除）
      if (scala.util.Random.nextInt(100) < 5) { // 5%の確率でクリーンアップ
        cleanupOldEntries(now)
      }

      nextFilter(requestHeader)
    }
  }

  // 古いエントリをクリーンアップ
  private def cleanupOldEntries(now: Long): Unit = {
    val tenMinutesAgo = now - (10 * 60 * 1000L)
    requestHistory.foreach { case (ip, history) =>
      val recent = history.filter(_ > tenMinutesAgo)
      if (recent.isEmpty) {
        requestHistory.remove(ip)
      } else {
        requestHistory.update(ip, recent)
      }
    }
  }
}
