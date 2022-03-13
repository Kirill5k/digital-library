package digitallibrary.core.book

import digitallibrary.core.common.types.IdType
import digitallibrary.core.category.CategoryId
import digitallibrary.core.auth.user.UserId
import io.circe.{Decoder, Encoder}

import java.time.LocalDate

opaque type BookId = String
object BookId extends IdType[BookId]

opaque type BookTitle = String
object BookTitle extends IdType[BookTitle]

final case class Book(
    id: BookId,
    userId: UserId,
    title: BookTitle,
    categoryId: CategoryId,
    date: LocalDate,
    note: Option[String],
    tags: Set[String]
)

final case class CreateBook(
    userId: UserId,
    title: BookTitle,
    categoryId: CategoryId,
    date: LocalDate,
    note: Option[String],
    tags: Set[String]
)
