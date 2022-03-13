package digitallibrary.core.auth.session

import com.comcast.ip4s.IpAddress
import digitallibrary.core.auth.user.UserId
import digitallibrary.core.common.types.IdType
import io.circe.{Decoder, Encoder}

import java.time.Instant

opaque type SessionId = String
object SessionId extends IdType[SessionId]

enum SessionStatus(val value: String):
  case Authenticated extends SessionStatus("authenticated")
  case LoggedOut     extends SessionStatus("logged-out")
  case Invalidated   extends SessionStatus("invalidated")

object SessionStatus {
  def from(value: String): Either[String, SessionStatus] =
    SessionStatus.values.find(_.value == value).toRight(s"Unexpected session status $value")

  inline given decodeSessionStatus: Decoder[SessionStatus] = Decoder[String].emap(SessionStatus.from)
  inline given encodeSessionStatus: Encoder[SessionStatus] = Encoder[String].contramap(_.value)
}

final case class SessionActivity(
    ipAddress: IpAddress,
    time: Instant
)

final case class Session(
    id: SessionId,
    userId: UserId,
    createdAt: Instant,
    active: Boolean,
    status: SessionStatus,
    lastRecordedActivity: Option[SessionActivity]
)

final case class CreateSession(
    userId: UserId,
    ipAddress: Option[IpAddress],
    time: Instant
)
