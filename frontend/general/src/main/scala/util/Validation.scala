package util

object Validation {

  /** 全角・半角スペースを含むすべての空白文字をトリミング
    * @param text
    *   トリミングする文字列
    * @return
    *   トリミング後の文字列
    */
  def trimAll(text: String): String = {
    if (text == null) return ""
    // JavaScriptの正規表現を使用して全角スペース（\u3000）も削除
    text.replaceAll("^[\\s\\u3000]+|[\\s\\u3000]+$", "")
  }

  /** 文字列が空白のみでないかチェック（全角スペース含む）
    * @param text
    *   チェックする文字列
    * @return
    *   空白のみの場合はfalse、有効な文字がある場合はtrue
    */
  def notBlank(text: String): Boolean = {
    if (text == null) false
    else trimAll(text).nonEmpty
  }
}
