package zio

import scala.concurrent.ExecutionContext

object PMR {

  class PMRFiber[A] extends AbstractPMRFiber[Nothing, A] {

    def step(v: Any, stack: List[Any => IO[Any, Any]]): Unit =
      stack.headOption.fold(
        notify(Exit.succeed(v.asInstanceOf[A]))
      ) { f =>
        val tail = stack.tail
        f(v) match {
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
