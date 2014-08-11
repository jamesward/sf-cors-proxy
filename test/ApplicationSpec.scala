import org.scalatestplus.play._
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.WS
import play.api.mvc.Results
import play.api.test.Helpers._

class ApplicationSpec extends PlaySpec with Results with OneServerPerSuite {

  def ws(path: String) = {
    val maybeForceToken = sys.env.get("FORCE_TOKEN")
    assume(maybeForceToken.isDefined)
    val url = s"http://localhost:$port" + path
    WS.url(url).withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer ${maybeForceToken.get}")
  }

  "query contacts" must {
    "return the contacts" in {
      val response = await(ws("/services/data/v30.0/query/?q=SELECT+name+from+Contact").get())
      response.status mustEqual OK
      (response.json \ "records").as[Seq[JsObject]].length must be > 0
      response.header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) mustBe 'defined
    }
  }

  "create contact with required fields" must {
    "create a contact" in {
      val json = Json.obj("LastName" -> "Foo")
      val response = await(ws("/services/data/v30.0/sobjects/Contact/").post(json))
      response.status mustEqual CREATED
      (response.json \ "id").asOpt[String] mustBe 'defined
      response.header(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN) mustBe 'defined
    }
  }

  "create contact without required fields" must {
    "fail" in {
      val json = Json.obj()
      val response = await(ws("/services/data/v30.0/sobjects/Contact/").post(json))
      response.status mustEqual BAD_REQUEST
      response.json.as[Seq[JsValue]].headOption.flatMap(jv => (jv \ "errorCode").asOpt[String]) mustEqual Some("REQUIRED_FIELD_MISSING")
    }
  }

}