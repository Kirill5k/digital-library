package digitallibrary.core.auth

import cats.effect.IO
import digitallibrary.core.ControllerSpec
import digitallibrary.core.auth.user.*
import digitallibrary.core.auth.session.*
import digitallibrary.core.common.actions.{Action, ActionDispatcher}
import digitallibrary.core.common.errors.AppError.{AccountAlreadyExists, InvalidEmailOrPassword}
import digitallibrary.core.fixtures.{Sessions, Users}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.http4s.{HttpDate, Method, Request, ResponseCookie, Status, Uri}

class AuthControllerSpec extends ControllerSpec {

  "An AuthController" when {
    "GET /auth/user" should {
      "return current account" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(userSvc.find(any[UserId])).thenReturn(IO.pure(Users.user))

        val req = Request[IO](uri = uri"/auth/user", method = Method.GET).addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        val resBody =
          s"""{
             |"id":"${Users.uid1}",
             |"email":"${Users.email}",
             |"firstName":"${Users.details.name.first}",
             |"lastName":"${Users.details.name.last}",
             |"settings":{"darkMode":null},
             |"registrationDate": "${Users.regDate}"
             |}""".stripMargin

        verifyJsonResponse(res, Status.Ok, Some(resBody))
        verify(userSvc).find(Sessions.sess.userId)
        verifyNoInteractions(disp, sessSvc)
      }
    }

    "PUT /auth/user/:id/settings" should {
      "return error when id in path is different from id in session" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val reqBody =
          """{
            |"currency":{"code":"USD","symbol":"$"},
            |"hideFutureTransactions":false,
            |"darkMode":false
            |}""".stripMargin

        val req = Request[IO](uri = uri"/auth/user/60e70e87fb134e0c1a271122/settings", method = Method.PUT)
          .withEntity(parseJson(reqBody))
          .addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        verifyJsonResponse(res, Status.Forbidden, Some("""{"message":"The current session belongs to a different user"}"""))
        verifyNoInteractions(disp, userSvc, sessSvc)
      }

      "return 204 when after updating account settings" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(userSvc.updateSettings(any[UserId], any[UserSettings])).thenReturn(IO.unit)

        val reqBody =
          """{
            |"currency":{"code":"USD","symbol":"$"},
            |"hideFutureTransactions":false,
            |"darkMode":false
            |}""".stripMargin

        val req = Request[IO](uri = Uri.unsafeFromString(s"/auth/user/${Users.uid1}/settings"), method = Method.PUT)
          .withEntity(parseJson(reqBody))
          .addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        verifyJsonResponse(res, Status.NoContent, None)
        verify(userSvc).updateSettings(Users.uid1, UserSettings(Some(false)))
        verifyNoInteractions(disp, sessSvc)
      }
    }

    "POST /auth/user/:id/password" should {
      "return error when id in path is different from id in session" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val reqBody = """{"newPassword":"new-pwd","currentPassword":"curr-pwd"}"""
        val req = Request[IO](uri = uri"/auth/user/60e70e87fb134e0c1a271122/password", method = Method.POST)
          .withEntity(parseJson(reqBody))
          .addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        verifyJsonResponse(res, Status.Forbidden, Some("""{"message":"The current session belongs to a different user"}"""))
        verifyNoInteractions(disp, userSvc, sessSvc)
      }

      "return 204 when after updating account password" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(userSvc.changePassword(any[ChangePassword])).thenReturn(IO.unit)
        when(sessSvc.invalidateAll(any[UserId])).thenReturn(IO.unit)
        when(sessSvc.create(any[CreateSession])).thenReturn(IO.pure(Sessions.sid2))

        val reqBody = """{"newPassword":"new-pwd","currentPassword":"curr-pwd"}"""
        val req = Request[IO](uri = Uri.unsafeFromString(s"/auth/user/${Users.uid1}/password"), method = Method.POST)
          .withEntity(parseJson(reqBody))
          .addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        val sessCookie = ResponseCookie(
          "session-id",
          Sessions.sid2.value,
          httpOnly = true,
          maxAge = Some(Long.MaxValue),
          expires = Some(HttpDate.MaxValue),
          path = Some("/")
        )
        verifyJsonResponse(res, Status.NoContent, None, List(sessCookie))
        verify(userSvc).changePassword(ChangePassword(Users.uid1, Password("curr-pwd"), Password("new-pwd")))
        verify(sessSvc).invalidateAll(Users.uid1)
        verify(sessSvc).create(any[CreateSession])
        verifyNoInteractions(disp)
      }
    }

    "POST /auth/user" should {
      "return bad request if email is already taken" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(userSvc.create(any[UserDetails], any[Password]))
          .thenReturn(IO.raiseError(AccountAlreadyExists(UserEmail("foo@bar.com"))))

        val reqBody = parseJson("""{"email":"foo@bar.com","password":"pwd","firstName":"John","lastName":"Bloggs"}""")
        val req     = Request[IO](uri = uri"/auth/user", method = Method.POST).withEntity(reqBody)
        val res     = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(req))

        verifyJsonResponse(
          res,
          Status.Conflict,
          Some("""{"message":"An account with email foo@bar.com already exists"}""")
        )
        verify(userSvc).create(
          UserDetails(UserEmail("foo@bar.com"), UserName("John", "Bloggs")),
          Password("pwd")
        )
        verifyNoInteractions(disp, sessSvc)
      }

      "return bad request when invalid request" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val reqBody = parseJson("""{"email":"foo@bar.com","password":"","firstName":"John","lastName":"Bloggs"}""")
        val req     = Request[IO](uri = uri"/auth/user", method = Method.POST).withEntity(reqBody)
        val res     = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(req))

        verifyJsonResponse(
          res,
          Status.UnprocessableEntity,
          Some("""{"message":"Password must not be empty"}""")
        )
        verifyNoInteractions(userSvc, sessSvc, disp)
      }

      "create new account and return 201" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(userSvc.create(any[UserDetails], any[Password])).thenReturn(IO.pure(Users.uid1))
        when(disp.dispatch(any[Action])).thenReturn(IO.unit)

        val reqBody = parseJson("""{"email":"foo@bar.com","password":"pwd","firstName":"John","lastName":"Bloggs"}""")
        val req     = Request[IO](uri = uri"/auth/user", method = Method.POST).withEntity(reqBody)
        val res     = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(req))

        verifyJsonResponse(res, Status.Created, Some(s"""{"id":"${Users.uid1}"}"""))
        verify(userSvc).create(
          UserDetails(UserEmail("foo@bar.com"), UserName("John", "Bloggs")),
          Password("pwd")
        )
        verify(disp).dispatch(Action.SetupNewUser(Users.uid1))
        verifyNoInteractions(sessSvc)
      }
    }

    "POST /auth/login" should {

      "return 422 on invalid json" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val req = Request[IO](uri = uri"/auth/login", method = Method.POST).withEntity("""{foo}""")
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(req))

        val responseBody = """{"message":"Invalid message body: Could not decode JSON: \"{foo}\""}"""
        verifyJsonResponse(res, Status.UnprocessableEntity, Some(responseBody))
        verifyNoInteractions(userSvc, sessSvc, disp)
      }

      "return bad req on parsing error" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val reqBody  = parseJson("""{"email":"foo","password":""}""")
        val res      = Request[IO](uri = uri"/auth/login", method = Method.POST).withEntity(reqBody)
        val response = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(res))

        val resBody = """{"message":"foo is not a valid email"}"""
        verifyJsonResponse(response, Status.UnprocessableEntity, Some(resBody))
        verifyNoInteractions(userSvc, sessSvc, disp)
      }

      "return unauthorized when invalid password or email" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(userSvc.login(any[UserEmail], any[Password]))
          .thenReturn(IO.raiseError(InvalidEmailOrPassword))

        val reqBody = parseJson("""{"email":"foo@bar.com","password":"bar"}""")
        val req     = Request[IO](uri = uri"/auth/login", method = Method.POST).withEntity(reqBody)
        val res     = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(req))

        verifyJsonResponse(res, Status.Unauthorized, Some("""{"message":"Invalid email or password"}"""))
        verify(userSvc).login(UserEmail("foo@bar.com"), Password("bar"))
        verifyNoInteractions(disp, sessSvc)
      }

      "return account on success and create session id cookie" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(userSvc.login(any[UserEmail], any[Password])).thenReturn(IO.pure(Users.user))
        when(sessSvc.create(any[CreateSession])).thenReturn(IO.pure(Sessions.sid))

        val reqBody = parseJson("""{"email":"foo@bar.com","password":"bar"}""")
        val req     = Request[IO](uri = uri"/auth/login", method = Method.POST).withEntity(reqBody)
        val res     = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(req))

        val resBody =
          s"""{
             |"id":"${Users.uid1}",
             |"email":"${Users.email}",
             |"firstName":"${Users.details.name.first}",
             |"lastName":"${Users.details.name.last}",
             |"settings":{"darkMode":null},
             |"registrationDate": "${Users.regDate}"
             |}""".stripMargin
        val sessCookie = ResponseCookie(
          "session-id",
          Sessions.sid.value,
          httpOnly = true,
          maxAge = Some(Long.MaxValue),
          expires = Some(HttpDate.MaxValue),
          path = Some("/")
        )
        verifyJsonResponse(res, Status.Ok, Some(resBody), List(sessCookie))
        verify(userSvc).login(UserEmail("foo@bar.com"), Password("bar"))
        verify(sessSvc).create(any[CreateSession])
        verifyNoInteractions(disp)
      }
    }

    "POST /auth/logout" should {
      "return forbidden if session id cookie is missing" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val req = Request[IO](uri = uri"/auth/logout", method = Method.POST)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        verifyJsonResponse(res, Status.Forbidden, Some("""{"message":"missing session-id cookie"}"""))
        verifyNoInteractions(userSvc, sessSvc, disp)
      }

      "return forbidden if session does not exist" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val req = Request[IO](uri = uri"/auth/logout", method = Method.POST).addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(None)).orNotFound.run(req))

        verifyJsonResponse(res, Status.Forbidden, Some("""{"message":"invalid session-id"}"""))
        verifyNoInteractions(userSvc, sessSvc, disp)
      }

      "return forbidden if session is inactive" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val exp = Sessions.sess.copy(active = false)
        val req = Request[IO](uri = uri"/auth/logout", method = Method.POST).addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(exp))).orNotFound.run(req))

        verifyJsonResponse(res, Status.Forbidden, Some("""{"message":"session is inactive"}"""))
        verifyNoInteractions(userSvc, sessSvc, disp)
      }

      "return forbidden if session id is malformed" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        val req = Request[IO](uri = uri"/auth/logout", method = Method.POST)
          .addCookie(sessIdCookie.copy(content = "f"))
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        verifyJsonResponse(res, Status.Forbidden, Some("""{"message":"invalid session-id format"}"""))
        verifyNoInteractions(userSvc, sessSvc, disp)
      }

      "delete session on success" in {
        val userSvc = mock[UserService[IO]]
        val sessSvc = mock[SessionService[IO]]
        val disp    = mock[ActionDispatcher[IO]]

        when(sessSvc.unauth(any[SessionId])).thenReturn(IO.unit)

        val req = Request[IO](uri = uri"/auth/logout", method = Method.POST).addCookie(sessIdCookie)
        val res = AuthController.make[IO](userSvc, sessSvc, disp).flatMap(_.routes(sessMiddleware(Some(Sessions.sess))).orNotFound.run(req))

        verifyJsonResponse(res, Status.NoContent, None)
        verify(sessSvc).unauth(Sessions.sid)
        verifyNoInteractions(disp, userSvc)
      }
    }
  }
}
