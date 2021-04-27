package kafka.issue

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}

trait AsyncUtils {

  def sequence[A, B](xs: Iterable[A])(f: A => Future[B])(implicit ec: ExecutionContext): Future[Queue[B]] =
    xs.foldLeft(Future.successful(Queue.empty[B])) {
      case (r, x) =>
        for {
          xs <- r
          b <- f(x)
        } yield xs.enqueue(b)
    }

}