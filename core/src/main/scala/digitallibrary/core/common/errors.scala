package digitallibrary.core.common

import digitallibrary.core.auth.user.*
import digitallibrary.core.category.*

object errors {

  sealed trait AppError extends Throwable {
    def message: String

    override def getMessage: String = message
  }

  object AppError {
    sealed trait Unauth extends AppError

    sealed trait NotFound extends AppError

    sealed trait Conflict extends AppError

    sealed trait BadReq extends AppError

    sealed trait Forbidden extends AppError

    final case class AccountAlreadyExists(email: UserEmail) extends Conflict {
      override def message: String = s"An account with email ${email.value} already exists"
    }

    final case class AccountDoesNotExist(id: UserId) extends NotFound {
      override def message: String = s"Account with id ${id.value} does not exist"
    }

    case object InvalidEmailOrPassword extends Unauth {
      override def message: String = "Invalid email or password"
    }

    case object InvalidPassword extends Unauth {
      override def message: String = "Entered password appears to be incorrect"
    }

    case object SomeoneElsesSession extends Forbidden {
      override def message: String = "The current session belongs to a different user"
    }

    case object IdMismatch extends BadReq {
      override def message: String = "The id supplied in the path does not match with the id in the request body"
    }

    final case class CategoryDoesNotExist(id: CategoryId) extends NotFound {
      override def message: String = s"Category with id ${id.value} does not exist"
    }

    final case class CategoryAlreadyExists(name: CategoryName) extends Conflict {
      override def message: String = s"A category with name ${name.value} already exists"
    }
  }
}
