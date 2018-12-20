package controllers

import javax.inject.Inject

import akka.util.ByteString
import play.api.libs.Codecs
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WSClient
import play.api.cache.CacheApi
import play.api.http.HeaderNames

class Application @Inject() (wsClient: WSClient, cache: CacheApi) (implicit ec: ExecutionContext) extends Controller {

  // Adds the CORS Header
  object CorsAction extends ActionBuilder[Request] {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] = {
      block(request).map(result => result.withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*", ACCESS_CONTROL_ALLOW_HEADERS -> "*"))
    }
  }

  class RequestWithInstance[A](val instance: String, request: Request[A]) extends WrappedRequest[A](request)

  object InstanceAction extends ActionBuilder[RequestWithInstance] with ActionTransformer[Request, RequestWithInstance] {

    override protected def transform[A](request: Request[A]): Future[RequestWithInstance[A]] = {

      def cacheMiss(auth: String): Future[String] = {

        def userinfo(url: String): Future[String] = {
          wsClient.url(url).get().map { response =>
            val instance = (response.json \ "profile").as[String].replace("https://", "").takeWhile(_ != '/')
            cache.set(Codecs.sha1(auth), instance)
            instance
          }
        }

        val token = auth.replace("Bearer ", "")

        val prodUrl = s"https://login.salesforce.com/services/oauth2/userinfo?oauth_token=$token"
        val sandboxUrl = s"https://test.salesforce.com/services/oauth2/userinfo?oauth_token=$token"

        userinfo(prodUrl).fallbackTo(userinfo(sandboxUrl))
      }

      def tryCache(auth: String): Future[String] = cache.get[String](Codecs.sha1(auth)).fold(cacheMiss(auth))(Future.successful)

      def makeReq(auth: String): Future[RequestWithInstance[A]] = {
        tryCache(auth).map { instance =>
         new RequestWithInstance[A](instance, request)
        }
      }

      request.headers.get(HeaderNames.AUTHORIZATION).fold(Future.failed[RequestWithInstance[A]](new Exception("Authorization Header Not Found")))(makeReq)
    }
  }

  private def proxyRequestResponse(request: Request[AnyContent], url: String): Future[Result] = {
    val requestHeaders = request.headers.toSimpleMap.toSeq
    val ws = wsClient.url(url).withMethod(request.method).withHeaders(requestHeaders: _*)
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
        ws.withBody(c.raw.asBytes().getOrElse(ByteString.empty))
      case _ =>
        ws
    }

    wsWithBody.stream().map { streamedResponse =>
      val responseHeaders = streamedResponse.headers.headers.filterKeys(_ != HeaderNames.CONTENT_TYPE).mapValues(_.headOption.getOrElse("")).toSeq
      val contentType = streamedResponse.headers.headers.getOrElse(HeaderNames.CONTENT_TYPE, Seq("application/octet-stream")).head
      Status(streamedResponse.headers.status).chunked(streamedResponse.body).withHeaders(responseHeaders:_*).as(contentType)
    }
  }

  def proxy(path: String) = (CorsAction andThen InstanceAction).async { request =>
    val url = s"https://${request.instance}${request.uri}"
    proxyRequestResponse(request, url)
  }

  def loginProxy(path: String) = CorsAction.async { request =>
    val prodUrl = s"https://login.salesforce.com${request.uri}"
    proxyRequestResponse(request, prodUrl).flatMap { result =>
      result.header.status match {
        case NOT_FOUND | BAD_REQUEST =>
          val sandboxUrl = s"https://test.salesforce.com${request.uri}"
          proxyRequestResponse(request, sandboxUrl)
        case _ =>
          Future.successful(result)
      }
    }
  }

  def options(path: String) = CorsAction {
    Ok.withHeaders(ACCESS_CONTROL_ALLOW_HEADERS -> "*")
  }

}
