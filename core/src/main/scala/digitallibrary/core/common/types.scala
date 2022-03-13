package digitallibrary.core.common

import mongo4cats.bson.ObjectId
import eu.timepit.refined.types.string.NonEmptyString

object types {

  trait IdType[Id]:
    def apply(id: String): Id   = id.asInstanceOf[Id]
    def apply(id: ObjectId): Id = apply(id.toHexString)
    extension (id: Id)
      def value: String        = id.asInstanceOf[String]
      def toObjectId: ObjectId = ObjectId(value)

  trait StringType[Str]:
    def apply(str: String): Str            = str.asInstanceOf[Str]
    extension (str: Str) def value: String = str.asInstanceOf[String]
}
