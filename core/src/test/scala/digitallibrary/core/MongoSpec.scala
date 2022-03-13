package digitallibrary.core

import mongo4cats.bson.{Document, ObjectId}
import digitallibrary.core.auth.user.*
import digitallibrary.core.category.*
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.Instant

trait MongoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  def categoryDoc(id: CategoryId, name: String, uid: Option[UserId] = None): Document =
    Document(
      Map(
        "_id"    -> ObjectId(id.value),
        "kind"   -> "expense",
        "name"   -> name,
        "icon"   -> "icon",
        "color"  -> "#2962FF",
        "userId" -> uid.map(id => ObjectId(id.value)).orNull
      )
    )

  def accDoc(
              id: UserId,
              email: UserEmail,
              password: PasswordHash = PasswordHash("password"),
              registrationDate: Instant = Instant.parse("2021-06-01T00:00:00Z")
            ): Document =
    Document(
      Map(
        "_id"              -> ObjectId(id.value),
        "email"            -> email.value,
        "password"         -> password.value,
        "name"             -> Document.parse("""{"first":"John","last":"Bloggs"}"""),
        "registrationDate" -> registrationDate
      )
    )
}
