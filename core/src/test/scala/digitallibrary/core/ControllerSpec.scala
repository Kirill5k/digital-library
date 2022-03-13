package digitallibrary.core

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.circe.*
import io.circe.parser.*
import org.http4s.{Response, ResponseCookie, Status}
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

trait ControllerSpec extends AnyWordSpec with Matchers {

  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def verifyJsonResponse(
      response: IO[Response[IO]],
      expectedStatus: Status,
      expectedBody: Option[String] = None,
      expectedCookies: List[ResponseCookie] = Nil
  ): Assertion =
    response
      .flatTap { res =>
        IO {
          res.status mustBe expectedStatus
          res.cookies must contain allElementsOf expectedCookies
        }
      }
      .flatMap { res =>
        expectedBody match {
          case Some(expectedJson) =>
            res.as[String].map { receivedJson =>
              parse(receivedJson) mustBe parse(expectedJson)
            }
          case None =>
            res.body.compile.toVector.map { receivedJson =>
              receivedJson mustBe empty
            }
        }
      }
      .unsafeRunSync()
}
