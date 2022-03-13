package digitallibrary.core.fixtures

import com.comcast.ip4s.IpAddress
import digitallibrary.core.auth.user.*
import digitallibrary.core.auth.session.*
import mongo4cats.bson.ObjectId
import org.http4s.RequestCookie

import java.time.Instant
import java.time.temporal.ChronoField

object Sessions {
  lazy val sid = SessionId(ObjectId().toHexString)
  lazy val ts  = Instant.now().`with`(ChronoField.MILLI_OF_SECOND, 0)
  lazy val ip  = IpAddress.fromString("127.0.0.1").get

  lazy val sa           = SessionActivity(ip, ts)
  lazy val sess         = Session(sid, Users.uid1, ts, true, SessionStatus.Authenticated, Some(sa))
  lazy val sessIdCookie = RequestCookie("session-id", sid.value)

  def create(
      uid: UserId = Users.uid1,
      ip: Option[IpAddress] = Some(ip),
      ts: Instant = ts
  ): CreateSession = CreateSession(uid, ip, ts)
}
