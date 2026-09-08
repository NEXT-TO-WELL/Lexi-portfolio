package utils

import org.scalatestplus.play.PlaySpec

class ValidationSpec extends PlaySpec {
  "boundedLimit" should {
    "keep query sizes within the configured range" in {
      Validation.boundedLimit(None, 20) mustBe 20
      Validation.boundedLimit(Some(0), 20) mustBe 1
      Validation.boundedLimit(Some(10000), 20) mustBe 100
    }
  }

  "isValidPassword" should {
    "enforce the minimum length and bcrypt byte limit" in {
      Validation.isValidPassword("1234567") mustBe false
      Validation.isValidPassword("12345678") mustBe true
      Validation.isValidPassword("あ" * 25) mustBe false
    }
  }
}
