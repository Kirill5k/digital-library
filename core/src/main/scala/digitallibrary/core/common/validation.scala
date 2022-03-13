package digitallibrary.core.common

import eu.timepit.refined.api.{Refined, RefinedTypeOps, Validate}
import eu.timepit.refined.string.MatchesRegex
import org.bson.types.ObjectId

object validation {
  type ColorString = String Refined MatchesRegex["^#[A-Za-z0-9]{3,6}$"]
  type EmailString = String Refined MatchesRegex["^[a-zA-Z0-9.]+@[a-zA-Z0-9]+\\.[a-zA-Z]+$"]

  type IdString = String Refined ValidId
  object ValidIdString extends RefinedTypeOps[IdString, String]

  final case class ValidId()

  inline given idValidate[T]: Validate.Plain[T, ValidId] =
    Validate.fromPredicate(id => ObjectId.isValid(id.toString), id => s"($id is valid id)", ValidId())
}
