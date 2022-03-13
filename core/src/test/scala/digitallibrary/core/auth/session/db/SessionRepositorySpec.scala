package digitallibrary.core.auth.session.db

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import com.comcast.ip4s.IpAddress
import digitallibrary.core.MongoSpec
import digitallibrary.core.auth.session.*
import digitallibrary.core.fixtures.{Sessions, Users}
import mongo4cats.bson.ObjectId
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase

import java.time.Instant
import java.time.temporal.ChronoField
import scala.concurrent.Future

class SessionRepositorySpec extends MongoSpec {

  override protected val mongoPort: Int = 12347

  "A SessionRepository" should {

    "create new sessions" in {
      withEmbeddedMongoDb { db =>
        val result = for {
          repo <- SessionRepository.make(db)
          sid  <- repo.create(Sessions.create())
          res  <- repo.find(sid, None)
        } yield (sid, res)

        result.map { case (sid, sess) =>
          sess mustBe Session(
            sid,
            Users.uid1,
            Sessions.ts,
            true,
            SessionStatus.Authenticated,
            Some(SessionActivity(Sessions.ip, Sessions.ts))
          ).some
        }
      }
    }

    "return empty option when session does not exist" in {
      withEmbeddedMongoDb { db =>
        val result = for {
          repo <- SessionRepository.make(db)
          res  <- repo.find(Sessions.sid, None)
        } yield res

        result.map(_ mustBe None)
      }
    }

    "unauth session" in {
      withEmbeddedMongoDb { db =>
        val result = for {
          repo <- SessionRepository.make(db)
          sid  <- repo.create(Sessions.create())
          _    <- repo.unauth(sid)
          res  <- repo.find(sid, None)
        } yield res

        result.map { s =>
          val sess = s.get
          sess.active mustBe false
          sess.status mustBe SessionStatus.LoggedOut
        }
      }
    }

    "invalidate all sessions" in {
      withEmbeddedMongoDb { db =>
        val result = for {
          repo <- SessionRepository.make(db)
          sid1 <- repo.create(Sessions.create())
          sid2 <- repo.create(Sessions.create())
          _    <- repo.invalidatedAll(Users.uid1)
          res  <- (repo.find(sid1, None), repo.find(sid2, None)).tupled
        } yield res

        result.map {
          case (Some(s1), Some(s2)) =>
            s1.status mustBe SessionStatus.Invalidated
            s1.active mustBe false
            s2.status mustBe SessionStatus.Invalidated
            s2.active mustBe false
          case _ => fail("unexpected match")
        }
      }
    }

    "update session last recorded activity on find" in {
      withEmbeddedMongoDb { db =>
        val activity = SessionActivity(Sessions.ip, Sessions.ts)
        val result = for {
          repo <- SessionRepository.make(db)
          sid  <- repo.create(Sessions.create())
          _    <- repo.find(sid, Some(activity))
          res  <- repo.find(sid, None)
        } yield res

        result.map { sess =>
          sess.flatMap(_.lastRecordedActivity) mustBe Some(activity)
        }
      }
    }
  }

  def withEmbeddedMongoDb[A](test: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://$mongoHost:$mongoPort")
        .use { client =>
          client.getDatabase("expense-tracker").flatMap(test)
        }
    }.unsafeToFuture()(IORuntime.global)
}
