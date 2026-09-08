package filters

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HttpFilters
import play.filters.cors.CORSFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import play.filters.hosts.AllowedHostsFilter

class FiltersSpec extends PlaySpec with GuiceOneAppPerSuite {
  "Filters" should {
    "retain Play security filters and append application filters" in {
      val filters = app.injector.instanceOf[HttpFilters].filters

      filters.exists(_.isInstanceOf[CSRFFilter]) mustBe true
      filters.exists(_.isInstanceOf[SecurityHeadersFilter]) mustBe true
      filters.exists(_.isInstanceOf[AllowedHostsFilter]) mustBe true
      filters.exists(_.isInstanceOf[CORSFilter]) mustBe true
      filters.exists(_.isInstanceOf[RateLimitFilter]) mustBe true
      filters.exists(_.isInstanceOf[AdminIPFilter]) mustBe true
    }
  }
}
