package dex

import dex.GlobalTimer.TimerOpsImplicits

import scala.collection.immutable.Queue
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Success, Try}

object Implicits {

  implicit final class FutureOps[T](val self: Future[T]) extends AnyVal {
    def safe(implicit ec: ExecutionContext): Future[Try[T]] = self.transform(x => Success(x))
  }

  implicit final class FutureCompanionOps(val self: Future.type) extends AnyVal {

    def inSeries[A, B](xs: Iterable[A])(f: A => Future[B])(implicit ec: ExecutionContext): Future[Queue[B]] =
      xs.foldLeft(Future.successful(Queue.empty[B])) {
        case (r, x) =>
          for {
            xs <- r
            _ <- GlobalTimer.instance.timer.sleep(10 millis)
            b <- f(x)
          } yield xs.enqueue(b)
      }

  }

}
