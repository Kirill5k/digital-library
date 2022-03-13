package digitallibrary.core.common

import com.comcast.ip4s.IpAddress
import io.circe.{Decoder, Encoder}

object json extends JsonCodecs

trait JsonCodecs {
  inline given ipDec: Decoder[IpAddress] = Decoder[String].emap(ip => IpAddress.fromString(ip).toRight(s"Invalid ip address $ip"))
  inline given ipEnc: Encoder[IpAddress] = Encoder[String].contramap(_.toUriString)
}
