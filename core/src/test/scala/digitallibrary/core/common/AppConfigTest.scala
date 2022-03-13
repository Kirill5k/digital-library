package digitallibrary.core.common

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import digitallibrary.core.common.config.AppConfig
import config.AppConfig
import digitallibrary.core.CatsSpec

class AppConfigSpec extends CatsSpec {

  "An AppConfig" should {

    "load itself from reference.conf" in {
      AppConfig.load[IO].unsafeToFuture().map { config =>
        config.server.host mustBe "0.0.0.0"
      }
    }
  }
}
