package digitallibrary.core.category

import digitallibrary.core.common.types.{IdType, StringType}
import digitallibrary.core.auth.user.UserId
import io.circe.{Decoder, Encoder}

opaque type CategoryId = String
object CategoryId extends IdType[CategoryId]

opaque type CategoryName = String
object CategoryName extends StringType[CategoryName]

opaque type CategoryIcon = String
object CategoryIcon extends StringType[CategoryIcon]

opaque type CategoryColor = String
object CategoryColor extends StringType[CategoryColor] {
  val Cyan       = CategoryColor("#84FFFF")
  val LightBlue  = CategoryColor("#00B0FF")
  val Blue       = CategoryColor("#2962FF")
  val Indigo     = CategoryColor("#304FFE")
  val DeepPurple = CategoryColor("#6200EA")
}

final case class Category(
    id: CategoryId,
    name: CategoryName,
    icon: CategoryIcon,
    color: CategoryColor,
    userId: Option[UserId]
)

final case class CreateCategory(
    name: CategoryName,
    icon: CategoryIcon,
    color: CategoryColor,
    userId: UserId
)
