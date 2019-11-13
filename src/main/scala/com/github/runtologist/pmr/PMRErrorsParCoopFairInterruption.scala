package zio

import scala.concurrent.ExecutionContext

object PMRErrorsParCoopFairInterruption extends AbstractPMR {

  class PMRFiber[E, A](implicit ec: ExecutionContext)
      extends AbstractPMRFiber[E, A] {

    private val yieldAfter = 10
    @volatile private var interrupted: Boolean = false

    override def interrupt: UIO[Exit[E, A]] = {
      interrupted = true
      await
    }

    def step(v: Any, stack: List[Any => IO[Any, Any]], count: Int): Unit =
      if (interrupted) {
        notify(Exit.interrupt)
      } else if (count >= yieldAfter) {
        ec.execute(() => step(v, stack, 0))
      } else {
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
              step(s.value, tail, count + 1)
            case fm: ZIO.FlatMap[_, _, _, _] =>
              step(
                v,
                tail
                  .prepended(fm.k.asInstanceOf[Any => IO[Any, Any]])
                  .prepended(_ => fm.zio),
                count + 1
              )
            // needed for done/join implementeation
            case ea: ZIO.EffectAsync[_, _, _] =>
              def callback(vv: ZIO[Any, Any, Any]): Unit =
                ec.execute(() => step((), tail.prepended(_ => vv), count + 1))

              ea.register(callback) match {
                case None     =>
                case Some(io) => step((), tail.prepended(_ => io), count + 1)
              }
            case f: ZIO.Fork[_, _, _] =>
              val fiber = new PMRFiber
              ec.execute(() => fiber.step(v, List(_ => f.value), count + 1))
              step(fiber, tail, count + 1)
            case ZIO.Yield =>
              ec.execute(() => step((), tail, count + 1))
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

  }

  override def unsafeRunAsync[E, A](io: => IO[E, A])(
      callback: Exit[E, A] => Unit
  )(
      implicit ec: ExecutionContext
  ): Unit = {
    val fiber = new PMRFiber
    fiber.register(callback)
    ec.execute(() => fiber.step((), List(_ => io), 0))
  }

}
