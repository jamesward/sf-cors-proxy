import play.api.mvc._
import play.filters.gzip.GzipFilter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Global extends WithFilters(new GzipFilter(), OnlyHttpsFilter)

object OnlyHttpsFilter extends Filter {
  def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    nextFilter(requestHeader).map { result =>
      if (requestHeader.secure || requestHeader.domain == "localhost")
        result
      else
        Results.MovedPermanently("https://" + requestHeader.domain + requestHeader.uri)
    }
  }
}