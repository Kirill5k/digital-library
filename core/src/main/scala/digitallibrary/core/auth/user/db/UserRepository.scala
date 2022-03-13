package digitallibrary.core.auth.user.db

import cats.effect.Async
import cats.syntax.flatMap.*
import cats.syntax.applicative.*
import cats.syntax.functor.*
import cats.syntax.applicativeError.*
import digitallibrary.core.auth.user.{PasswordHash, User, UserDetails, UserEmail, UserId, UserSettings}
import digitallibrary.core.common.db.Repository
import digitallibrary.core.common.errors.AppError.{AccountAlreadyExists, AccountDoesNotExist}
import digitallibrary.core.common.json.given
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.collection.operations.{Filter, Update}
import mongo4cats.collection.MongoCollection
import mongo4cats.database.MongoDatabase

trait UserRepository[F[_]] extends Repository[F]:
  def find(aid: UserId): F[User]
  def findBy(email: UserEmail): F[Option[User]]
  def create(details: UserDetails, password: PasswordHash): F[UserId]
  def updateSettings(aid: UserId, settings: UserSettings): F[Unit]
  def updatePassword(aid: UserId)(password: PasswordHash): F[Unit]

final private class LiveUserRepository[F[_]: Async](
    private val collection: MongoCollection[F, UserEntity]
) extends UserRepository[F] {

  override def findBy(email: UserEmail): F[Option[User]] =
    collection
      .find(Filter.eq(Field.Email, email.value))
      .first
      .map(_.map(_.toDomain))

  override def create(details: UserDetails, password: PasswordHash): F[UserId] =
    collection
      .count(Filter.eq(Field.Email, details.email.value))
      .flatMap {
        case 0 =>
          val createAcc = UserEntity.create(details, password)
          collection.insertOne(createAcc).as(UserId(createAcc._id.toHexString))
        case _ =>
          AccountAlreadyExists(details.email).raiseError[F, UserId]
      }

  override def find(aid: UserId): F[User] =
    collection
      .find(idEq(aid.value))
      .first
      .flatMap {
        case Some(user) => user.toDomain.pure[F]
        case None       => AccountDoesNotExist(aid).raiseError[F, User]
      }

  override def updateSettings(aid: UserId, settings: UserSettings): F[Unit] =
    collection
      .updateOne(idEq(aid.value), Update.set("settings", settings))
      .flatMap(errorIfNoMatches(AccountDoesNotExist(aid)))

  override def updatePassword(aid: UserId)(password: PasswordHash): F[Unit] =
    collection
      .updateOne(idEq(aid.value), Update.set("password", password.value))
      .flatMap(errorIfNoMatches(AccountDoesNotExist(aid)))
}

object UserRepository:
  def make[F[_]: Async](db: MongoDatabase[F]): F[UserRepository[F]] =
    db.getCollectionWithCodec[UserEntity]("users")
      .map(_.withAddedCodec[UserSettings])
      .map(coll => LiveUserRepository[F](coll))
