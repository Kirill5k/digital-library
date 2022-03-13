package digitallibrary.core.auth.user.db

import digitallibrary.core.auth.user.*
import mongo4cats.bson.ObjectId

import java.time.Instant

final case class UserEntity(
    _id: ObjectId,
    email: String,
    name: UserName,
    password: String,
    settings: Option[UserSettings],
    registrationDate: Instant
) {
  def toDomain: User =
    User(
      id = UserId(_id),
      email = UserEmail(email),
      name = name,
      password = PasswordHash(password),
      settings = settings.getOrElse(UserSettings.Default),
      registrationDate = registrationDate
    )
}

object UserEntity {
  def create(details: UserDetails, password: PasswordHash): UserEntity =
    UserEntity(
      ObjectId(),
      details.email.value,
      details.name,
      password.value,
      Some(UserSettings.Default),
      Instant.now()
    )
}
