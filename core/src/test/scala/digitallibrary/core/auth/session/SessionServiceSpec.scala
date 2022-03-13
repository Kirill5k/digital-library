package digitallibrary.core.auth.session

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import digitallibrary.core.CatsSpec
import digitallibrary.core.fixtures.{Sessions, Users}
import digitallibrary.core.auth.user.UserId
import digitallibrary.core.auth.session.db.SessionRepository

class SessionServiceSpec extends CatsSpec {

  "A SessionService" should {

    "create new session" in {
      val repo = mock[SessionRepository[IO]]
      when(repo.create(any[CreateSession])).thenReturn(IO.pure(Sessions.sid))

      val result = for {
        svc <- SessionService.make(repo)
        sid <- svc.create(Sessions.create())
      } yield sid

      result.unsafeToFuture().map { res =>
        verify(repo).create(Sessions.create())
        res mustBe Sessions.sid
      }
    }

    "return existing session" in {
      val repo = mock[SessionRepository[IO]]
      when(repo.find(any[SessionId], any[Option[SessionActivity]])).thenReturn(IO.pure(Some(Sessions.sess)))

      val result = for {
        svc  <- SessionService.make(repo)
        sess <- svc.find(Sessions.sid, Some(Sessions.sa))
      } yield sess

      result.unsafeToFuture().map { res =>
        verify(repo).find(Sessions.sid, Some(Sessions.sa))
        res mustBe Some(Sessions.sess)
      }
    }

    "unauth session" in {
      val repo = mock[SessionRepository[IO]]
      when(repo.unauth(Sessions.sid)).thenReturn(IO.unit)

      val result = for {
        svc <- SessionService.make(repo)
        res <- svc.unauth(Sessions.sid)
      } yield res

      result.unsafeToFuture().map { res =>
        verify(repo).unauth(Sessions.sid)
        res mustBe ()
      }
    }

    "invalidate all sessions" in {
      val repo = mock[SessionRepository[IO]]
      when(repo.invalidatedAll(any[UserId])).thenReturn(IO.unit)

      val result = for {
        svc <- SessionService.make(repo)
        res <- svc.invalidateAll(Users.uid1)
      } yield res

      result.unsafeToFuture().map { res =>
        verify(repo).invalidatedAll(Users.uid1)
        res mustBe ()
      }
    }
  }
}
