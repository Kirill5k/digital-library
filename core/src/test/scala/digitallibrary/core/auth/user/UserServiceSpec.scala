package digitallibrary.core.auth.user

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import digitallibrary.core.fixtures.Users.*
import digitallibrary.core.CatsSpec
import digitallibrary.core.auth.user.db.UserRepository
import digitallibrary.core.common.errors.AppError.{InvalidEmailOrPassword, InvalidPassword}

class UserServiceSpec extends CatsSpec {

  "A UserService" when {
    "create" should {
      "return account id on success" in {
        val (repo, encr) = mocks
        when(encr.hash(any[Password])).thenReturn(IO.pure(hash))
        when(repo.create(any[UserDetails], any[PasswordHash])).thenReturn(IO.pure(uid1))

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.create(details, pwd)
        } yield res

        result.unsafeToFuture().map { res =>
          verify(encr).hash(pwd)
          verify(repo).create(details, hash)
          res mustBe uid1
        }
      }
    }

    "updateSettings" should {
      "return unit on success" in {
        val (repo, encr) = mocks
        when(repo.updateSettings(any[UserId], any[UserSettings])).thenReturn(IO.unit)

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.updateSettings(uid1, UserSettings.Default)
        } yield res

        result.unsafeToFuture().map { res =>
          verify(repo).updateSettings(uid1, UserSettings.Default)
          verifyNoInteractions(encr)
          res mustBe ()
        }
      }
    }

    "updatePassword" should {
      val cp = ChangePassword(uid1, pwd, Password("new-password"))

      "return unit on success" in {
        val (repo, encr) = mocks
        when(encr.isValid(any[Password], any[PasswordHash])).thenReturn(IO.pure(true))
        when(encr.hash(any[Password])).thenReturn(IO.pure(hash))
        when(repo.find(any[UserId])).thenReturn(IO.pure(user))
        when(repo.updatePassword(any[UserId])(any[PasswordHash])).thenReturn(IO.unit)

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.changePassword(cp)
        } yield res

        result.unsafeToFuture().map { res =>
          verify(repo).find(cp.id)
          verify(encr).isValid(cp.currentPassword, user.password)
          verify(encr).hash(cp.newPassword)
          verify(repo).updatePassword(cp.id)(hash)
          res mustBe ()
        }
      }

      "return error when passwords do not match" in {
        val (repo, encr) = mocks
        when(repo.find(any[UserId])).thenReturn(IO.pure(user))
        when(encr.isValid(any[Password], any[PasswordHash])).thenReturn(IO.pure(false))

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.changePassword(cp)
        } yield res

        result.attempt.unsafeToFuture().map { res =>
          verify(repo).find(cp.id)
          verify(encr).isValid(cp.currentPassword, user.password)
          verifyNoMoreInteractions(repo, encr)
          res mustBe Left(InvalidPassword)
        }
      }
    }

    "find" should {
      "return account on success" in {
        val (repo, encr) = mocks
        when(repo.find(any[UserId])).thenReturn(IO.pure(user))

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.find(uid1)
        } yield res

        result.unsafeToFuture().map { res =>
          verifyNoInteractions(encr)
          verify(repo).find(uid1)
          res mustBe user
        }
      }
    }

    "login" should {

      "return account on success" in {
        val (repo, encr) = mocks
        when(repo.findBy(any[UserEmail])).thenReturn(IO.pure(Some(user)))
        when(encr.isValid(any[Password], any[PasswordHash])).thenReturn(IO.pure(true))

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.login(details.email, pwd)
        } yield res

        result.unsafeToFuture().map { res =>
          verify(repo).findBy(details.email)
          verify(encr).isValid(pwd, hash)
          res mustBe user
        }
      }

      "return error when account does not exist" in {
        val (repo, encr) = mocks
        when(repo.findBy(any[UserEmail])).thenReturn(IO.pure(None))

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.login(details.email, pwd)
        } yield res

        result.attempt.unsafeToFuture().map { res =>
          verify(repo).findBy(details.email)
          verifyNoInteractions(encr)
          res mustBe Left(InvalidEmailOrPassword)
        }
      }

      "return error when password doesn't match" in {
        val (repo, encr) = mocks
        when(repo.findBy(any[UserEmail])).thenReturn(IO.pure(Some(user)))
        when(encr.isValid(any[Password], any[PasswordHash])).thenReturn(IO.pure(false))

        val result = for {
          service <- UserService.make[IO](repo, encr)
          res     <- service.login(details.email, pwd)
        } yield res

        result.attempt.unsafeToFuture().map { res =>
          verify(repo).findBy(details.email)
          verify(encr).isValid(pwd, hash)
          res mustBe Left(InvalidEmailOrPassword)
        }
      }
    }
  }

  def mocks: (UserRepository[IO], PasswordEncryptor[IO]) =
    (mock[UserRepository[IO]], mock[PasswordEncryptor[IO]])
}
