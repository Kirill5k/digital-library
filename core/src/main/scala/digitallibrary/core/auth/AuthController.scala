package digitallibrary.core.auth

import cats.Monad
import cats.effect.Temporal
import cats.syntax.flatMap.*
import cats.syntax.applicative.*
import cats.syntax.apply.*
import cats.syntax.functor.*
import cats.syntax.alternative.*
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import digitallibrary.core.auth.user.{ChangePassword, Password, User, UserDetails, UserEmail, UserId, UserName, UserService, UserSettings}
import digitallibrary.core.auth.session.{CreateSession, Session, SessionAuth, SessionService}
import digitallibrary.core.common.actions.{Action, ActionDispatcher}
import digitallibrary.core.common.errors.AppError.SomeoneElsesSession
import digitallibrary.core.common.validation.*
import digitallibrary.core.common.http.Controller
import io.circe.generic.auto.*
import io.circe.refined.*
import org.bson.types.ObjectId
import org.http4s.{AuthedRoutes, HttpRoutes}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.server.{AuthMiddleware, Router}
import org.typelevel.log4cats.Logger

import java.time.Instant

final class AuthController[F[_]: Logger](
    private val userService: UserService[F],
    private val sessionService: SessionService[F],
    private val dispatcher: ActionDispatcher[F]
)(using
    F: Temporal[F]
) extends Controller[F] {
  import AuthController.*

  object UserIdPath {
    def unapply(cid: String): Option[UserId] =
      ObjectId.isValid(cid).guard[Option].as(UserId(cid))
  }

  private val prefixPath = "/auth"

  private val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "user" =>
      withErrorHandling {
        for {
          create <- req.as[CreateUserRequest]
          aid    <- userService.create(create.userDetails, create.userPassword)
          _      <- dispatcher.dispatch(Action.SetupNewUser(aid))
          res    <- Created(CreateUserResponse(aid.value))
        } yield res
      }
    case req @ POST -> Root / "login" =>
      withErrorHandling {
        for {
          login <- req.as[LoginRequest]
          time  <- Temporal[F].realTime.map(t => Instant.ofEpochMilli(t.toMillis))
          acc   <- userService.login(login.userEmail, login.userPassword)
          sid   <- sessionService.create(CreateSession(acc.id, req.from, time))
          res   <- Ok(UserView.from(acc))
        } yield res.addCookie(SessionAuth.responseCookie(sid))
      }
  }

  private val authedRoutes: AuthedRoutes[Session, F] =
    AuthedRoutes.of {
      case GET -> Root / "user" as session =>
        withErrorHandling {
          userService.find(session.userId).map(UserView.from).flatMap(Ok(_))
        }
      case authedReq @ PUT -> Root / "user" / UserIdPath(id) / "settings" as session =>
        withErrorHandling {
          for {
            _   <- F.ensure(id.pure[F])(SomeoneElsesSession)(_ == session.userId)
            req <- authedReq.req.as[UpdateUserSettingsRequest]
            _   <- userService.updateSettings(id, req.toDomain)
            res <- NoContent()
          } yield res
        }
      case authedReq @ POST -> Root / "user" / UserIdPath(id) / "password" as session =>
        withErrorHandling {
          for {
            _    <- F.ensure(id.pure[F])(SomeoneElsesSession)(_ == session.userId)
            req  <- authedReq.req.as[ChangePasswordRequest]
            _    <- userService.changePassword(req.toDomain(id))
            _    <- sessionService.invalidateAll(session.userId)
            time <- Temporal[F].realTime.map(t => Instant.ofEpochMilli(t.toMillis))
            sid  <- sessionService.create(CreateSession(id, authedReq.req.from, time))
            res  <- NoContent()
          } yield res.addCookie(SessionAuth.responseCookie(sid))
        }
      case POST -> Root / "logout" as session =>
        withErrorHandling {
          sessionService.unauth(session.id) *> NoContent()
        }
    }

  def routes(authMiddleware: AuthMiddleware[F, Session]): HttpRoutes[F] =
    Router(
      prefixPath -> authMiddleware(authedRoutes),
      prefixPath -> routes
    )
}

object AuthController {

  final case class CreateUserRequest(
      email: EmailString,
      firstName: NonEmptyString,
      lastName: NonEmptyString,
      password: NonEmptyString
  ) {
    def userDetails: UserDetails =
      UserDetails(UserEmail.from(email), UserName(firstName.value, lastName.value))

    def userPassword: Password = Password(password.value)
  }

  final case class CreateUserResponse(id: String)

  final case class LoginRequest(
      email: EmailString,
      password: NonEmptyString
  ) {
    def userEmail    = UserEmail.from(email)
    def userPassword = Password(password.value)
  }

  final case class UserView(
      id: String,
      firstName: String,
      lastName: String,
      email: String,
      settings: UserSettings,
      registrationDate: Instant
  )

  object UserView {
    def from(acc: User): UserView =
      UserView(
        acc.id.value,
        acc.name.first,
        acc.name.last,
        acc.email.value,
        acc.settings,
        acc.registrationDate
      )
  }

  final case class UpdateUserSettingsRequest(darkMode: Option[Boolean]) {
    def toDomain: UserSettings = UserSettings(darkMode = darkMode)
  }

  final case class ChangePasswordRequest(
      currentPassword: NonEmptyString,
      newPassword: NonEmptyString
  ) {
    def toDomain(id: UserId): ChangePassword =
      ChangePassword(id, Password(currentPassword.value), Password(newPassword.value))
  }

  def make[F[_]: Temporal: Logger](
      userService: UserService[F],
      sessionService: SessionService[F],
      dispatcher: ActionDispatcher[F]
  ): F[AuthController[F]] =
    Monad[F].pure(AuthController[F](userService, sessionService, dispatcher))
}
