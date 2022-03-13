package digitallibrary.core.common.http

import org.http4s.HttpRoutes
import io.circe.Codec
import sttp.tapir.generic.SchemaDerivation
import sttp.tapir.json.circe.TapirJsonCirce

final case class ErrorResponse(message: String) derives Codec.AsObject

trait Controller[F[_]] extends TapirJsonCirce with SchemaDerivation:
  def routes: HttpRoutes[F]
