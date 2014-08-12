package controllers

import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws.WS
import play.api.cache.Cache
import play.api.http.HeaderNames
import play.api.Play.current

object Application extends Controller {

  def proxy(path: String) = RequestWithInstance.async { request =>
    val url = s"https://${request.instance}${request.uri}"
    val requestHeaders = request.headers.toSimpleMap.toSeq
    val ws = WS.url(url).withMethod(request.method).withHeaders(requestHeaders: _*)
    val wsWithBody = request.body match {
      case c: AnyContentAsFormUrlEncoded =>
        ws.withBody(c.data)
      case c: AnyContentAsJson =>
        ws.withBody(c.json)
      case c: AnyContentAsXml =>
        ws.withBody(c.xml)
      case c: AnyContentAsText =>
        ws.withBody(c.txt)
      case c: AnyContentAsRaw =>
        ws.withBody(c.raw.asBytes().getOrElse(Array.empty))
      case _ =>
        ws
    }

    wsWithBody.stream().map { case (response, enumerator) =>
      val responseHeaders = response.headers.mapValues(_.headOption.getOrElse("")).toSeq
      Status(response.status).chunked(enumerator).withHeaders(responseHeaders: _*)
    }
  }

  def options(path: String) = CorsAction {
    Action { request =>
      Ok.withHeaders(ACCESS_CONTROL_ALLOW_HEADERS -> Seq(AUTHORIZATION, CONTENT_TYPE).mkString(","))
    }
  }

}

// Adds the CORS Header
case class CorsAction[A](action: Action[A]) extends Action[A] {

  def apply(request: Request[A]): Future[Result] = {
    action(request).map(result => result.withHeaders(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN -> "*"))
  }

  lazy val parser = action.parser
}


class RequestWithInstance[A](val instance: String, request: Request[A]) extends WrappedRequest[A](request)

// Figures out the Salesforce Instance to make the request to
object RequestWithInstance extends ActionBuilder[RequestWithInstance] {
  def invokeBlock[A](request: Request[A], block: (RequestWithInstance[A]) => Future[Result]): Future[Result] = {

    def cacheMiss(auth: String): Future[String] = {
      val token = auth.replace("Bearer ", "")
      val url = s"https://login.salesforce.com/services/oauth2/userinfo?oauth_token=$token"
      WS.url(url).get().map { response =>
        val instance = (response.json \ "profile").as[String].replace("https://", "").takeWhile(_ != '/')
        Cache.set(auth, instance)
        instance
      }
    }

    def tryCache(auth: String): Future[String] = Cache.getAs[String](auth).fold(cacheMiss(auth))(Future.successful)

    def makeReq(auth: String): Future[Result] = {
      tryCache(auth).flatMap { instance =>
        block(new RequestWithInstance[A](instance, request))
      }
    }

    request.headers.get(HeaderNames.AUTHORIZATION).fold(Future.successful(Results.Unauthorized("Authorization Header Not Found")))(makeReq)
  }

  override protected def composeAction[A](action: Action[A]): Action[A] = new CorsAction(action)
}
