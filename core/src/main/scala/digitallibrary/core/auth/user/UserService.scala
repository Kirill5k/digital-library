package digitallibrary.core.auth.user

import cats.MonadError
import cats.syntax.flatMap.*
import cats.syntax.apply.*
import cats.syntax.functor.*
import cats.syntax.applicativeError.*
import digitallibrary.core.auth.user.db.UserRepository
import digitallibrary.core.common.errors.AppError.{InvalidEmailOrPassword, InvalidPassword}

private enum LoginResult:
  case Fail
  case Success(user: User)

trait UserService[F[_]]:
  def create(details: UserDetails, password: Password): F[UserId]
  def login(email: UserEmail, password: Password): F[User]
  def find(uid: UserId): F[User]
  def updateSettings(uid: UserId, settings: UserSettings): F[Unit]
  def changePassword(cp: ChangePassword): F[Unit]

final private class LiveUserService[F[_]](
    private val repository: UserRepository[F],
    private val encryptor: PasswordEncryptor[F]
)(using
    F: MonadError[F, Throwable]
) extends UserService[F] {

  override def create(details: UserDetails, password: Password): F[UserId] =
    encryptor.hash(password).flatMap(h => repository.create(details, h))

  override def login(email: UserEmail, password: Password): F[User] =
    repository
      .findBy(email)
      .flatMap {
        case Some(acc) => encryptor.isValid(password, acc.password).map(if (_) LoginResult.Success(acc) else LoginResult.Fail)
        case None      => F.pure(LoginResult.Fail)
      }
      .flatMap {
        case LoginResult.Fail       => InvalidEmailOrPassword.raiseError[F, User]
        case LoginResult.Success(a) => F.pure(a)
      }

  override def find(uid: UserId): F[User] =
    repository.find(uid)

  override def updateSettings(uid: UserId, settings: UserSettings): F[Unit] =
    repository.updateSettings(uid, settings)

  override def changePassword(cp: ChangePassword): F[Unit] =
    repository
      .find(cp.id)
      .flatMap(acc => encryptor.isValid(cp.currentPassword, acc.password))
      .flatMap {
        case false => InvalidPassword.raiseError[F, PasswordHash]
        case true  => encryptor.hash(cp.newPassword)
      }
      .flatMap(repository.updatePassword(cp.id))

}

object UserService:
  def make[F[_]](repo: UserRepository[F], encr: PasswordEncryptor[F])(using F: MonadError[F, Throwable]): F[UserService[F]] =
    F.pure(LiveUserService[F](repo, encr))
