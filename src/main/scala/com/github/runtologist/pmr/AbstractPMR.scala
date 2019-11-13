package zio

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.github.runtologist.pmr.OneShot

trait AbstractPMR {

  def unsafeRunAsync[E, A](io: => IO[E, A])(callback: Exit[E, A] => Unit)(
      implicit ec: ExecutionContext
  ): Unit

  def unsafeRunToFuture[E, A](
      io: => IO[E, A]
  )(implicit ec: ExecutionContext): Future[A] = {
    val promise = scala.concurrent.Promise[A]
    unsafeRunAsync(io) { exit =>
      promise.success(
        exit
          .getOrElse(_ => throw new IllegalStateException("No managed errors."))
      )
    }
    promise.future
  }

  def unsafeRunSync[E, A](
      io: => IO[E, A]
  )(implicit ec: ExecutionContext): Exit[E, A] = {
    val result = new OneShot[Exit[E, A]]
    unsafeRunAsync(io)(result.set)
    result.get
  }

}
