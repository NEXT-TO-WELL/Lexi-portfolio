package util

import scala.scalajs.js

object Debouncer {

  /** デバウンス処理を行う関数を生成
    * @param delayMs
    *   遅延時間（ミリ秒）
    * @param fn
    *   実行する関数
    * @return
    *   デバウンスされた関数
    */
  def debounce[T](delayMs: Int)(fn: T => Unit): T => Unit = {
    var timerId: Option[js.timers.SetTimeoutHandle] = None

    (arg: T) => {
      // 既存のタイマーをキャンセル
      timerId.foreach(js.timers.clearTimeout)

      // 新しいタイマーをセット
      timerId = Some(js.timers.setTimeout(delayMs) {
        fn(arg)
        timerId = None
      })
    }
  }
}
