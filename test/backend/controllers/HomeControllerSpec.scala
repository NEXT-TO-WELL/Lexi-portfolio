package controllers

import org.scalatestplus.play.*
import play.api.test.*
import play.api.test.Helpers.*

class HomeControllerSpec extends PlaySpec {
  "HomeController" should {
    "render the home page without requiring a database" in {
      val controller = new HomeController(stubControllerComponents(), null)
      val result = controller.index("").apply(FakeRequest(GET, "/"))
      status(result) mustBe OK
      contentType(result) mustBe Some("text/html")
    }
  }
}
