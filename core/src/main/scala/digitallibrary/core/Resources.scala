package digitallibrary.core

import cats.effect.{Async, Resource}
import digitallibrary.core.common.config.{AppConfig, MongoConfig}
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase

final class Resources[F[_]] private (
    val mongo: MongoDatabase[F]
)

object Resources:

  private def mongoDb[F[_]: Async](config: MongoConfig): Resource[F, MongoDatabase[F]] =
    MongoClient
      .fromConnectionString[F](config.connectionUri)
      .evalMap(_.getDatabase("digital-library"))

  def make[F[_]: Async](config: AppConfig): Resource[F, Resources[F]] =
    mongoDb[F](config.mongo).map(m => new Resources[F](m))
