// app/modules/JobModule.scala
package modules // ← パッケージ名が application.conf と一致していること

import com.google.inject.AbstractModule
import javax.inject._
import org.apache.pekko.actor.ActorSystem
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import services.TrendService
import play.api.Configuration

// 1. スケジューラ本体
@Singleton
class TrendUpdateScheduler @Inject() (
    system: ActorSystem,
    trendService: TrendService,
    config: Configuration
)(implicit ec: ExecutionContext) {

  private val logger = play.api.Logger(getClass)

  if (config.get[Boolean]("jobs.trends.enabled")) {
    logger.info("Trend update scheduler initialized - will run every 1 hour")
    system.scheduler.scheduleWithFixedDelay(0.seconds, 1.hour)(() => {
      trendService.updateAllTrends()
    })
  }
}

// 2. Playに教えるためのModuleクラス
class JobModule extends AbstractModule {
  override def configure(): Unit = {
    // TrendUpdateScheduler を「起動時に即座に作成する」ように設定
    bind(classOf[TrendUpdateScheduler]).asEagerSingleton()
  }
}
