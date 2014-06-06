package controllers

import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws.{Response, WS}
import scala.collection.JavaConverters.{mapAsScalaMapConverter, asScalaBufferConverter}
import play.api.libs.ws.WS.WSRequestHolder
import play.api.libs.iteratee.Enumerator
import play.api.cache.Cache
import play.api.http.HeaderNames
import play.api.Play.current

object Application extends Controller {

  implicit class ResponseToResult(val self: Response) extends AnyVal {
    def toResult: SimpleResult = {
      val headers = self.ahcResponse.getHeaders.asScala.mapValues(_.asScala.head)
      val data = Enumerator.fromStream(self.getAHCResponse.getResponseBodyAsStream)
      Status(self.status).chunked(data).withHeaders(headers.toSeq: _*)
    }
  }

  implicit class RequestToWSRequestHolder(val self: RequestWithInstance[AnyContent]) extends AnyVal {
    def ws: WSRequestHolder = {
      val url = s"https://${self.instance}${self.uri}"
      WS.url(url).withHeaders(self.headers.toSimpleMap.toSeq: _*)
    }
  }

  def get(path: String) = RequestWithInstance.async { request =>
    request.ws.get().map(_.toResult)
  }

  def post(path: String) = RequestWithInstance.async { request =>
    //request.ws.post(request).map(_.toResult)
    Future.successful(NotImplemented)
  }

  def options(path: String) = CorsAction {
    Action { request =>
      Ok.withHeaders("Access-Control-Allow-Headers" -> "Authorization")
    }
  }

}

// Adds the CORS Header
case class CorsAction[A](action: Action[A]) extends Action[A] {

  def apply(request: Request[A]): Future[SimpleResult] = {
    action(request).map(result => result.withHeaders("Access-Control-Allow-Origin" -> "*"))
  }

  lazy val parser = action.parser
}


class RequestWithInstance[A](val instance: String, request: Request[A]) extends WrappedRequest[A](request)

// Figures out the Salesforce Instance to make the request to
object RequestWithInstance extends ActionBuilder[RequestWithInstance] {
  def invokeBlock[A](request: Request[A], block: (RequestWithInstance[A]) => Future[SimpleResult]): Future[SimpleResult] = {

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

    def makeReq(auth: String): Future[SimpleResult] = {
      tryCache(auth).flatMap { instance =>
        block(new RequestWithInstance[A](instance, request))
      }
    }

    request.headers.get(HeaderNames.AUTHORIZATION).fold(Future.successful(Results.Unauthorized("Authorization Header Not Found")))(makeReq)
  }

  override protected def composeAction[A](action: Action[A]): Action[A] = new CorsAction(action)
}