package utils

object Validation {
  val MinimumPasswordLength = 8
  val MaximumBcryptPasswordBytes = 72

  def boundedLimit(value: Option[Int], default: Int, maximum: Int = 100): Int =
    value.getOrElse(default).max(1).min(maximum)

  def boundedOffset(value: Option[Int], maximum: Int = 10000): Int =
    value.getOrElse(0).max(0).min(maximum)

  def isValidPassword(password: String): Boolean =
    Option(password).exists { value =>
      value.length >= MinimumPasswordLength && value
        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        .length <= MaximumBcryptPasswordBytes
    }

  def lengthVal(len: Int, line: String): Boolean = {
    Option(line).map(_.length).getOrElse(0) <= len
  }

  /** 全角・半角スペースを含むすべての空白文字をトリミング
    * @param text
    *   トリミングする文字列
    * @return
    *   トリミング後の文字列
    */
  def trimAll(text: String): String = {
    if (text == null) return ""
    // \s (半角空白類) と \u3000 (全角スペース) の両方を削除
    text.replaceAll("^[\\s\\u3000]+|[\\s\\u3000]+$", "")
  }

  /** 文字列が空白のみでないかチェック（全角スペース含む）
    * @param text
    *   チェックする文字列
    * @return
    *   空白のみの場合はfalse、有効な文字がある場合はtrue
    */
  def notBlank(text: String): Boolean = {
    Option(text).exists(t => trimAll(t).nonEmpty)
  }

  /** 文字列が空白のみでないかつ、長さ制限内かチェック
    * @param len
    *   最大長
    * @param text
    *   チェックする文字列
    * @return
    *   有効な入力の場合はtrue
    */
  def isValidInput(len: Int, text: String): Boolean = {
    notBlank(text) && lengthVal(len, text)
  }

  /** 読みがひらがな、全角カタカナ、全角伸ばし棒のみで構成されているかチェック 半角カタカナ・半角長音は許可しない
    */
  def isValidReading(reading: String): Boolean = {
    if (reading == null || reading.isEmpty) return false

    reading.forall { c =>
      val isHiragana = c >= 'ぁ' && c <= 'ん' // ひらがな
      val isFullKatakana = c >= 'ァ' && c <= 'ヶ' // 全角カタカナ
      val isFullLongVowel = c == 'ー' // 全角伸ばし棒

      isHiragana || isFullKatakana || isFullLongVowel
    }
  }
}
