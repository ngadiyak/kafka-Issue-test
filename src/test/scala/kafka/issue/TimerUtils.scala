package kafka.issue

import java.util.{Timer, TimerTask}
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

trait TimerUtils extends {

  private val timer = new Timer(true)

  def schedule[A](f: => Future[A], delay: FiniteDuration): Future[A] = {
    val p = Promise[A]()
    val task = new TimerTask {
      override def run(): Unit = p.completeWith(f)
    }

    try timer.schedule(task, delay.toMillis)
    catch {
      case NonFatal(e) => p.failure(e)
    }
    p.future
  }

  def sleep(term: FiniteDuration): Future[Unit] = schedule(Future.unit, term)

}
