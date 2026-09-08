package integration

import models.db.Word
import repositories.WordRepository

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class WordRepositoryIntegrationSpec extends DatabaseIntegrationSpec {
  private val repository = app.injector.instanceOf[WordRepository]

  "WordRepository" should {
    "create, find, and delete a word in the test database" in {
      val wordName = s"test-word-${UUID.randomUUID()}"
      val wordId = Await.result(repository.create(Word(None, wordName)), 5.seconds)

      try {
        val found = Await.result(repository.findByWordId(wordId), 5.seconds)
        found.map(_.wordMain) must contain(wordName)
      } finally {
        Await.result(repository.delete(Some(wordId)), 5.seconds) mustBe 1
      }
    }
  }
}
