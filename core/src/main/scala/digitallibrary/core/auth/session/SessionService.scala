package digitallibrary.core.auth.session

import cats.Monad
import digitallibrary.core.auth.user.UserId
import digitallibrary.core.auth.session.db.SessionRepository

trait SessionService[F[_]]:
  def create(cs: CreateSession): F[SessionId]
  def find(sid: SessionId, activity: Option[SessionActivity]): F[Option[Session]]
  def unauth(sid: SessionId): F[Unit]
  def invalidateAll(uid: UserId): F[Unit]

final private class LiveSessionService[F[_]](
    private val repository: SessionRepository[F]
) extends SessionService[F] {

  override def create(cs: CreateSession): F[SessionId] =
    repository.create(cs)

  override def find(sid: SessionId, activity: Option[SessionActivity]): F[Option[Session]] =
    repository.find(sid, activity)

  override def unauth(sid: SessionId): F[Unit] =
    repository.unauth(sid)

  override def invalidateAll(uid: UserId): F[Unit] =
    repository.invalidatedAll(uid)
}

object SessionService:
  def make[F[_]: Monad](repo: SessionRepository[F]): F[SessionService[F]] =
    Monad[F].pure(LiveSessionService[F](repo))
