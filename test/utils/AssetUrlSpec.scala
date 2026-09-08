package utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AssetUrlSpec extends AnyWordSpec with Matchers {
  "AssetUrl.versioned" should {
    "append a content version to a public asset URL" in {
      val url = AssetUrl.versioned("stylesheets/main.css")

      url should startWith("/assets/stylesheets/main.css?v=")
      url.split("\\?v=").last should fullyMatch regex "[0-9a-f]{16}"
    }
  }
}
