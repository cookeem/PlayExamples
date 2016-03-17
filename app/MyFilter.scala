/**
 * Created by cookeem on 16/1/22.
 */

import akka.stream.Materializer
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api._
import play.api.mvc.{Result, RequestHeader, Filter}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

/**
 * Created by cookeem on 16/1/22.
 */
import javax.inject.Inject
import play.api.http.HttpFilters
import play.filters.gzip.GzipFilter

//filter ###############################################
//Play2.5.0需要依赖注入
class LoggingFilter @Inject() (implicit val mat: Materializer) extends Filter {
  def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis
    nextFilter(requestHeader).map { result =>
      val endTime = System.currentTimeMillis
      val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
      val datetime = new DateTime()
      val dateStr = datetime.toString(dateFormat)

      val requestTime = endTime - startTime
      Logger.info(s""" [$dateStr] [${requestHeader.remoteAddress}] [${requestHeader.headers("User-Agent")}] ${requestHeader.method}/${result.header.status} ${requestHeader.uri} ${requestTime}ms""")
      result.withHeaders("Request-Time" -> requestTime.toString)
    }
  }
}

class MyFilter @Inject() (
  gzip: GzipFilter,
  log: LoggingFilter
) extends HttpFilters {
  val filters = Seq(gzip, log)
}