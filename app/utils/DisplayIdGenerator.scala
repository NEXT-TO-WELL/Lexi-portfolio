package utils

import java.security.SecureRandom

object DisplayIdGenerator {
  private val chars = "0123456789abcdef"
  private val random = new SecureRandom()

  // 長さを変数化（将来の変更が簡単）
  // 現在: 8文字 (16^8 = 約43億パターン、1万人まで安全)
  // 将来: 10文字に変更可能 (16^10 = 約1.1兆パターン)
  private val LENGTH = 8

  /** ランダムなdisplayIdを生成
    * @return
    *   16進数の文字列（例: "a3f8b2e1"）
    */
  def generate(): String = {
    (1 to LENGTH).map(_ => chars(random.nextInt(chars.length))).mkString
  }

  /** 衝突チェック付きでdisplayIdを生成
    * @param existingIds
    *   既存のdisplayIdのセット
    * @param maxRetries
    *   最大リトライ回数
    * @return
    *   重複しないdisplayId (失敗時はNone)
    */
  def generateUnique(existingIds: Set[String], maxRetries: Int = 10): Option[String] = {
    (1 to maxRetries).view
      .map(_ => generate())
      .find(!existingIds.contains(_))
  }
}
