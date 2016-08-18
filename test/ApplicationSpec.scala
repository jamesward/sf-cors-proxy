import org.scalatestplus.play._
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.mvc.Results
import play.api.test.Helpers._

class ApplicationSpec extends PlaySpec with Results with OneServerPerSuite {

  val wsClient = app.injector.instanceOf[WSClient]

  def ws(path: String) = {
    val maybeForceToken = sys.env.get("FORCE_TOKEN")
    assume(maybeForceToken.isDefined)
    val url = s"http://localhost:$port" + path
    wsClient.url(url).withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer ${maybeForceToken.get}")
  }

  "token" must {
    "work with valid credentials" in {
      for {
        username <- sys.env.get("FORCE_USERNAME")
        password <- sys.env.get("FORCE_PASSWORD")
        clientId <- sys.env.get("FORCE_CLIENT_ID")
        clientSecret <- sys.env.get("FORCE_CLIENT_SECRET")
      } yield {
        val body = Map(
          "grant_type" -> "password",
          "client_id" -> clientId,
          "client_secret" -> clientSecret,
          "username" -> username,
          "password" -> password
        ).mapValues(Seq(_))

        val response = await(wsClient.url(s"http://localhost:$port/services/oauth2/token").post(body))
        response.status mustEqual OK
      }
    }
  }

  "userinfo" must {
    "work with valid credentials" in {
      val maybeForceToken = sys.env.get("FORCE_TOKEN")
      assume(maybeForceToken.isDefined)
      val response = await(wsClient.url(s"http://localhost:$port/services/oauth2/userinfo?oauth_token=${maybeForceToken.get}").get())
      response.status mustEqual OK
    }
    "not work with invalid credentials" in {
      val response = await(wsClient.url(s"http://localhost:$port/services/oauth2/userinfo?oauth_token=asdf").get())
      response.status mustEqual FORBIDDEN
    }
  }

  "API" must {
    "work" in {
      val response = await(ws("/services/data").get())
      response.status mustEqual OK
      response.header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) mustBe 'defined
      response.header(HeaderNames.CONTENT_TYPE) mustEqual Some("application/json;charset=UTF-8")
    }
  }

  "query contacts" must {
    "return the contacts" in {
      val response = await(ws("/services/data/v30.0/query/?q=SELECT+name+from+Contact").get())
      response.status mustEqual OK
      (response.json \ "records").as[Seq[JsObject]].length must be > 0
      response.header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) mustBe 'defined
      response.header(HeaderNames.CONTENT_TYPE) mustEqual Some("application/json;charset=UTF-8")
    }
  }

  "bad query" must {
    "fail correctly" in {
      val response = await(ws("/services/data/v30.0/query/?FOO").get())
      response.status mustEqual BAD_REQUEST
      response.json.as[Seq[JsValue]].headOption.flatMap(jv => (jv \ "errorCode").asOpt[String]) mustEqual Some("MALFORMED_QUERY")
      response.header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) mustBe 'defined
      response.header(HeaderNames.CONTENT_TYPE) mustEqual Some("application/json;charset=UTF-8")
    }
  }

  "create contact with required fields" must {
    "create a contact" in {
      val json = Json.obj("LastName" -> "Foo")
      val response = await(ws("/services/data/v30.0/sobjects/Contact/").post(json))
      response.status mustEqual CREATED
      (response.json \ "id").asOpt[String] mustBe 'defined
      response.header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) mustBe 'defined
      response.header(HeaderNames.CONTENT_TYPE) mustEqual Some("application/json;charset=UTF-8")
    }
  }

  "create contact without required fields" must {
    "fail" in {
      val json = Json.obj()
      val response = await(ws("/services/data/v30.0/sobjects/Contact/").post(json))
      response.status mustEqual BAD_REQUEST
      response.json.as[Seq[JsValue]].headOption.flatMap(jv => (jv \ "errorCode").asOpt[String]) mustEqual Some("REQUIRED_FIELD_MISSING")
      response.header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) mustBe 'defined
      response.header(HeaderNames.CONTENT_TYPE) mustEqual Some("application/json;charset=UTF-8")
    }
  }

  "Apex REST" must {
    "work" in {
      val response = await(ws("/services/apexrest/Contacts").get())
      response.status mustEqual OK
      response.json.as[Seq[JsValue]].headOption.flatMap(_.\("Id").asOpt[String]).filter(_.nonEmpty) mustBe 'defined
      response.header(HeaderNames.CONTENT_TYPE) mustEqual Some("application/json;charset=UTF-8")
    }
  }

}
