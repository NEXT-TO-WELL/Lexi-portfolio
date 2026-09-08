package general.mypage

import component.{Modal, ModalType}
import general.ui.EditModal
import general.mypage.MyPostsTab.*
import models.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class MyPostsActions(state: MyPostsState, refreshProfile: () => Unit) {
  import state.*

  def selectTab(tab: MyPostsTab): Unit =
    if (!isFetchingVar.now()) {
      activeTabVar.set(tab)
      if (!state.isLoaded(tab)) fetch(tab)
    }

  def refreshCurrentTab(): Unit = fetch(activeTabVar.now())

  def fetch(tab: MyPostsTab): Unit = {
    isFetchingVar.set(true)
    val request = tab match {
      case Words    => MyPostsApi.fetchWords().map(items => () => wordsVar.set(items))
      case Meanings => MyPostsApi.fetchMeanings().map(items => () => meaningsVar.set(items))
      case Examples => MyPostsApi.fetchExamples().map(items => () => examplesVar.set(items))
      case Threads  => MyPostsApi.fetchThreads().map(items => () => threadsVar.set(items))
      case Comments => MyPostsApi.fetchComments().map(items => () => commentsVar.set(items))
    }

    request.onComplete {
      case scala.util.Success(update) =>
        update()
        state.markLoaded(tab)
        isFetchingVar.set(false)
      case scala.util.Failure(error) =>
        org.scalajs.dom.console.error(s"Fetch failed: ${error.getMessage}")
        state.markLoaded(tab)
        isFetchingVar.set(false)
    }
  }

  def deleteMeaning(meaningId: Long): Unit =
    delete(MyPostsApi.deleteMeaning(meaningId)) {
      meaningsVar.update(_.filterNot(_.meaningView.meaning.meaningId.contains(meaningId)))
    }

  def deleteExample(exampleId: Long): Unit =
    delete(MyPostsApi.deleteExample(exampleId)) {
      examplesVar.update(_.filterNot(_.exampleView.example.exampleId.contains(exampleId)))
    }

  def deleteThread(threadId: Long): Unit =
    delete(MyPostsApi.deleteThread(threadId)) {
      threadsVar.update(_.filterNot(_.threadId == threadId))
    }

  def deleteComment(commentId: Long): Unit =
    delete(MyPostsApi.deleteComment(commentId)) {
      commentsVar.update(_.filterNot(_.comment.commentId.contains(commentId)))
    }

  def updateMeaning(meaningId: Long, text: String, isJoke: Boolean): Unit = {
    val trimmed = validated(text, "語釈")
    trimmed.foreach { value =>
      val request = MeaningPostRequest(wordId = 0L, tagId = 0L, meaningText = value, isJoke = isJoke)
      util.ErrorHandler.handle(
        MyPostsApi.updateMeaning(meaningId, request),
        onSuccess = { updated =>
          meaningsVar.update(_.map { item =>
            if (item.meaningView.meaning.meaningId.contains(meaningId)) item.copy(meaningView = updated)
            else item
          })
          EditModal.close()
        },
        showSuccessModal = true,
        successMessage = "語釈を更新しました"
      )
    }
  }

  def updateExample(exampleId: Long, text: String, isJoke: Boolean): Unit = {
    val trimmed = validated(text, "用例")
    trimmed.foreach { value =>
      val request = ExamplePostRequest(wordId = 0L, tagId = 0L, exampleText = value, isJoke = isJoke)
      util.ErrorHandler.handle(
        MyPostsApi.updateExample(exampleId, request),
        onSuccess = { updated =>
          examplesVar.update(_.map { item =>
            if (item.exampleView.example.exampleId.contains(exampleId)) item.copy(exampleView = updated)
            else item
          })
          EditModal.close()
        },
        showSuccessModal = true,
        successMessage = "用例を更新しました"
      )
    }
  }

  def updateThread(threadId: Long, title: String): Unit = {
    val trimmed = validated(title, "タイトル")
    trimmed.foreach { value =>
      util.ErrorHandler.handle(
        MyPostsApi.updateThread(threadId, ThreadUpdateRequest(value)),
        onSuccess = { updated =>
          threadsVar.update(_.map(thread => if (thread.threadId == threadId) updated else thread))
          EditModal.close()
        },
        showSuccessModal = true,
        successMessage = "スレッドを更新しました"
      )
    }
  }

  def updateComment(commentId: Long, text: String): Unit = {
    val trimmed = validated(text, "コメント")
    trimmed.foreach { value =>
      util.ErrorHandler.handle(
        MyPostsApi.updateComment(commentId, CommentUpdateByTextRequest(value)),
        onSuccess = { updated =>
          commentsVar.update(_.map(comment => if (comment.comment.commentId.contains(commentId)) updated else comment))
          EditModal.close()
        },
        showSuccessModal = true,
        successMessage = "コメントを更新しました"
      )
    }
  }

  private def delete(request: Future[Unit])(update: => Unit): Unit =
    request.foreach { _ =>
      update
      refreshProfile()
    }

  private def validated(value: String, label: String): Option[String] = {
    val trimmed = util.Validation.trimAll(value)
    if (util.Validation.notBlank(trimmed)) Some(trimmed)
    else {
      EditModal.isSubmitting.set(false)
      Modal.show("エラー", s"${label}に空白のみは使用できません", ModalType.Info)
      None
    }
  }
}
