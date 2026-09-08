package general.ui

import org.scalajs.dom
import upickle.default.*
import shared.api.ContentQuoteViewValue

object DraftStorage {
  private val KeyPrefix = "lexi:draft:v1:"

  final case class PostDraft(
      word: String = "",
      reading: String = "",
      tag: String = "",
      meaning: String = "",
      example: String = ""
  ) derives ReadWriter

  def postDraft: PostDraft = readJson("post").getOrElse(PostDraft())

  def savePostDraft(draft: PostDraft): Unit =
    if (Seq(draft.word, draft.reading, draft.tag, draft.meaning, draft.example).exists(_.trim.nonEmpty)) {
      writeJson("post", draft)
    } else {
      discard("post")
    }

  def loadText(form: String, context: String): String =
    readRaw(key(form, context)).getOrElse("")

  def saveText(form: String, context: String, text: String): Unit =
    if (text.trim.nonEmpty) writeRaw(key(form, context), text)
    else discard(form, context)

  def loadQuote(form: String, context: String): Option[ContentQuoteViewValue] =
    readRaw(key(s"$form:quote", context)).flatMap { value =>
      try Some(read[ContentQuoteViewValue](value))
      catch { case _: Throwable => None }
    }

  def saveQuote(form: String, context: String, quote: Option[ContentQuoteViewValue]): Unit =
    quote match {
      case Some(value) => writeRaw(key(s"$form:quote", context), write(value))
      case None        => discard(s"$form:quote", context)
    }

  def discard(form: String, context: String = ""): Unit =
    remove(key(form, context))

  private def key(form: String, context: String): String =
    s"$KeyPrefix$form${if (context.nonEmpty) s":$context" else ""}"

  private def readJson(draftName: String): Option[PostDraft] =
    readRaw(key(draftName, "")).flatMap { value =>
      try Some(read[PostDraft](value))
      catch { case _: Throwable => None }
    }

  private def writeJson(draftName: String, value: PostDraft): Unit =
    writeRaw(key(draftName, ""), write(value))

  private def readRaw(key: String): Option[String] =
    try Option(dom.window.localStorage.getItem(key))
    catch { case _: Throwable => None }

  private def writeRaw(key: String, value: String): Unit =
    try dom.window.localStorage.setItem(key, value)
    catch { case _: Throwable => () }

  private def remove(key: String): Unit =
    try dom.window.localStorage.removeItem(key)
    catch { case _: Throwable => () }
}
