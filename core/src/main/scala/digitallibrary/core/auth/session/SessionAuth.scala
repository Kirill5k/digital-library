package digitallibrary.core.auth.session

import cats.data.{Kleisli, OptionT}
import cats.effect.Temporal
import cats.syntax.flatMap.*
import cats.syntax.either.*
import cats.syntax.applicative.*
import cats.syntax.functor.*
import io.circe.generic.auto.*
import org.bson.types.ObjectId
import org.http4s.{AuthedRoutes, HttpDate, Request, ResponseCookie}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.server.AuthMiddleware

import java.time.Instant

object SessionAuth {
  private val SessionIdCookie = "session-id"

  def middleware[F[_]](
      obtainSession: (SessionId, Option[SessionActivity]) => F[Option[Session]]
  )(using
      F: Temporal[F]
  ): AuthMiddleware[F, Session] = {
    val dsl = new Http4sDsl[F] {}; import dsl.*

    val onFailure: AuthedRoutes[String, F] =
      Kleisli(req => OptionT.liftF(Forbidden(ErrorResponse(req.context)).map(_.removeCookie(SessionIdCookie))))

    val getValidSession: Kleisli[F, Request[F], Either[String, Session]] =
      Kleisli { req =>
        req.cookies
          .find(_.name == SessionIdCookie)
          .toRight("missing session-id cookie")
          .map(_.content)
          .flatMap(sid => Either.cond(ObjectId.isValid(sid), SessionId(sid), "invalid session-id format"))
          .fold(
            _.asLeft[Session].pure[F],
            sid =>
              F.realTime.flatMap { time =>
                val currentTime = Instant.ofEpochMilli(time.toMillis)
                val activity    = req.from.map(ip => SessionActivity(ip, currentTime))
                obtainSession(sid, activity).map {
                  case Some(s) if s.active => s.asRight[String]
                  case Some(_)             => "session is inactive".asLeft[Session]
                  case None                => "invalid session-id".asLeft[Session]
                }
              }
          )
      }

    AuthMiddleware(getValidSession, onFailure)
  }

  def responseCookie(sid: SessionId): ResponseCookie =
    ResponseCookie(
      SessionIdCookie,
      sid.value,
      httpOnly = true,
      maxAge = Some(Long.MaxValue),
      expires = Some(HttpDate.MaxValue),
      path = Some("/")
    )
}
