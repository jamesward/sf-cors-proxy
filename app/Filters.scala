import javax.inject.Inject

import akka.stream.Materializer
import play.api.http.DefaultHttpFilters
import play.api.mvc.Filter
import play.filters.gzip.GzipFilter
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

class Filters @Inject() (gzip: GzipFilter, httpsOnlyFilter: HttpsOnlyFilter) extends DefaultHttpFilters(gzip, httpsOnlyFilter)

class HttpsOnlyFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      import play.api.http.HeaderNames
      requestHeader.headers.get(HeaderNames.X_FORWARDED_PROTO).filter(_ != "https").fold(result) { proto =>
        import play.api.mvc.Results
        Results.MovedPermanently("https://" + requestHeader.host + requestHeader.uri)
      }
    }
  }

}
