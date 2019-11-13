package zio

import scala.concurrent.ExecutionContext

object PMRErrorsParCoop extends AbstractPMR {

  class PMRFiber[E, A](implicit ec: ExecutionContext)
      extends AbstractPMRFiber[E, A] {

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
          // needed for done/join implementeation
          case ea: ZIO.EffectAsync[_, _, _] =>
            def callback(vv: ZIO[Any, Any, Any]): Unit =
              ec.execute(() => step((), tail.prepended(_ => vv)))

            ea.register(callback) match {
              case None     =>
              case Some(io) => step((), tail.prepended(_ => io))
            }
          case f: ZIO.Fork[_, _, _] =>
            val fiber = new PMRFiber
            ec.execute(() => fiber.step(v, List(_ => f.value)))
            step(fiber, tail)
          case ZIO.Yield =>
            ec.execute(() => step((), tail))
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

  override def unsafeRunAsync[E, A](io: => IO[E, A])(
      callback: Exit[E, A] => Unit
  )(
      implicit ec: ExecutionContext
  ): Unit = {
    val fiber = new PMRFiber
    fiber.register(callback)
    ec.execute(() => fiber.step((), List(_ => io)))
  }

}
