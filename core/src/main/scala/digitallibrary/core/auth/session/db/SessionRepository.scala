package digitallibrary.core.auth.session.db

import cats.effect.Async
import cats.syntax.functor.*
import io.circe.generic.auto.*
import digitallibrary.core.auth.session.{CreateSession, Session, SessionActivity, SessionId, SessionStatus}
import digitallibrary.core.auth.user.UserId
import digitallibrary.core.common.db.Repository
import digitallibrary.core.common.json.given
import mongo4cats.database.MongoDatabase
import mongo4cats.circe.*
import mongo4cats.collection.operations.Update
import mongo4cats.collection.MongoCollection

trait SessionRepository[F[_]] extends Repository[F]:
  def create(cs: CreateSession): F[SessionId]
  def find(sid: SessionId, activity: Option[SessionActivity]): F[Option[Session]]
  def unauth(sid: SessionId): F[Unit]
  def invalidatedAll(aid: UserId): F[Unit]

final private class LiveSessionRepository[F[_]: Async](
    private val collection: MongoCollection[F, SessionEntity]
) extends SessionRepository[F] {

  private val logoutUpdate     = Update.set(Field.Status, SessionStatus.LoggedOut).set("active", false)
  private val invalidateUpdate = Update.set(Field.Status, SessionStatus.Invalidated).set("active", false)

  override def create(cs: CreateSession): F[SessionId] = {
    val createSession = SessionEntity.create(cs)
    collection.insertOne(createSession).as(SessionId(createSession._id.toHexString))
  }

  override def find(sid: SessionId, activity: Option[SessionActivity]): F[Option[Session]] = {
    val idFilter = idEq(sid.value)
    activity
      .map(sa => collection.findOneAndUpdate(idFilter, Update.set("lastRecordedActivity", sa)))
      .getOrElse(collection.find(idFilter).first)
      .map(_.map(_.toDomain))
  }

  override def unauth(sid: SessionId): F[Unit] =
    collection.updateOne(idEq(sid.value), logoutUpdate).void

  override def invalidatedAll(aid: UserId): F[Unit] =
    collection.updateMany(userIdEq(aid), invalidateUpdate).void
}

object SessionRepository {
  def make[F[_]: Async](db: MongoDatabase[F]): F[SessionRepository[F]] =
    db
      .getCollectionWithCodec[SessionEntity]("sessions")
      .map(_.withAddedCodec[SessionActivity].withAddedCodec[SessionStatus])
      .map(coll => LiveSessionRepository[F](coll))
}
