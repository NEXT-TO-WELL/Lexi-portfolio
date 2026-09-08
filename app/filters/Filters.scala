package filters

import javax.inject.Inject
import play.api.http.{DefaultHttpFilters, EnabledFilters}

class Filters @Inject() (
    enabledFilters: EnabledFilters,
    rateLimitFilter: RateLimitFilter,
    adminIPFilter: AdminIPFilter
) extends DefaultHttpFilters((enabledFilters.filters ++ Seq(rateLimitFilter, adminIPFilter))*)
