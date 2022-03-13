package digitallibrary.core.fixtures

import mongo4cats.bson.ObjectId
import digitallibrary.core.auth.user.*

import java.time.Instant
import java.time.temporal.ChronoField

object Users {
  lazy val uid1 = UserId(ObjectId().toHexString)
  lazy val uid2 = UserId(ObjectId().toHexString)

  lazy val regDate = Instant.now.`with`(ChronoField.MILLI_OF_SECOND, 0)
  lazy val hash    = PasswordHash("hash")
  lazy val pwd     = Password("pwd")
  lazy val email   = UserEmail("acc1@et.com")
  lazy val details = UserDetails(email, UserName("John", "Bloggs"))
  lazy val user    = User(uid1, details.email, details.name, hash, UserSettings.Default, regDate)
}
