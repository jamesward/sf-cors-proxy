package controllers

import play.api.mvc._
import play.api.{Logger, Play}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.ws.{Response, WS}
import scala.collection.JavaConverters.{mapAsScalaMapConverter, asScalaBufferConverter}
import play.api.libs.ws.WS.WSRequestHolder
import play.api.libs.iteratee.Enumerator

object Application extends Controller {

  implicit class ResponseToResult(val self: Response) extends AnyVal {
    def toResult: SimpleResult = {
      val headers = self.ahcResponse.getHeaders.asScala.mapValues(_.asScala.head)
      val data = Enumerator.fromStream(self.getAHCResponse.getResponseBodyAsStream)
      Status(self.status).chunked(data).withHeaders(headers.toSeq: _*)
    }
  }

  implicit class RequestToWSRequestHolder(val self: RequestWithProxyHost[AnyContent]) extends AnyVal {
    def ws: WSRequestHolder = {
      val url = s"https://${self.proxyHost}${self.uri}"
      WS.url(url).withHeaders(self.headers.toSimpleMap.toSeq: _*)
    }
  }

  def get(path: String) = RequestWithProxyHost.async { request =>
    request.ws.get().map(_.toResult)
  }

  def post(path: String) = RequestWithProxyHost.async { request =>
    //request.ws.post(request).map(_.toResult)
    Future.successful(NotImplemented)
  }

  def options(path: String) = Action { request =>
    Ok.withHeaders("Access-Control-Allow-Origin" -> "*", "Access-Control-Allow-Headers" -> "Authorization")
  }

}


class RequestWithProxyHost[A](val proxyHost: String, request: Request[A]) extends WrappedRequest[A](request)

object RequestWithProxyHost extends ActionBuilder[RequestWithProxyHost] {
  def invokeBlock[A](request: Request[A], block: (RequestWithProxyHost[A]) => Future[SimpleResult]): Future[SimpleResult] = {
    Play.current.configuration.getString("proxy.host").map { proxyHost =>
        block(new RequestWithProxyHost[A](proxyHost, request)).map(result => result.withHeaders("Access-Control-Allow-Origin" -> "*"))
    } getOrElse {
      Logger.error("Proxy host not defined. Set the PROXY_HOST env var.")
      Future.successful(Results.InternalServerError)
    }
  }
}