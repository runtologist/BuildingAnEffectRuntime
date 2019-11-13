package zio

import scala.concurrent.ExecutionContext

object PMRErrors {

  class PMRFiber[E, A] extends AbstractPMRFiber[E, A] {

    def step(v: Any, stack: List[Any => IO[Any, Any]]): Unit =
      stack.headOption.fold(
        notify(Exit.succeed(v.asInstanceOf[A]))
      ) { f =>
        val tail = stack.tail
        f(v) match {
          case e: ZIO.Fail[Any, Any] =>
            notify(
              Exit.fail(
                e.fill(() => ZTrace(fiberId = 0L, Nil, Nil, None))
                  .failures
                  .head
                  .asInstanceOf[E]
              )
            )
          case s: ZIO.Succeed[_] =>
            step(s.value, tail)
          case fm: ZIO.FlatMap[_, _, _, _] =>
            step(
              v,
              tail
                .prepended(fm.k.asInstanceOf[Any => IO[Any, Any]])
                .prepended(_ => fm.zio)
            )
          case other =>
            notify(
              Exit.die(
                new IllegalStateException(
                  s"not implemented: ${other.getClass}"
                )
              )
            )
        }
      }

  }

  def unsafeRunAsync[E, A](
      zio: => ZIO[Any, E, A]
  )(
      k: Exit[E, A] => Unit
  )(
      implicit ec: ExecutionContext
  ): Unit = {
    val fiber = new PMRFiber
    fiber.register(k)
    ec.execute(() => fiber.step((), List(_ => zio)))
  }

}
