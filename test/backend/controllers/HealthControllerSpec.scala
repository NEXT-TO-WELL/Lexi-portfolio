package controllers

import org.scalatestplus.play.*
import play.api.test.*
import play.api.test.Helpers.*

class HealthControllerSpec extends PlaySpec {
  "GET /health" should {
    "return a plain text 200 response" in {
      val result = new HealthController(stubControllerComponents()).check.apply(FakeRequest(GET, "/health"))
      status(result) mustBe OK
      contentType(result) mustBe Some("text/plain")
      contentAsString(result) mustBe "ok"
    }
  }
}
