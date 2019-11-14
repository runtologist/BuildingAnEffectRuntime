package zio

import com.github.runtologist.runtime.{Fiber => PoorMansFiber}

object ZioInterpreters {

  val succeedFlatMap: PoorMansFiber.Interpreter = {
    case (s: ZIO.Succeed[_], _, stack, _) => Right((s.value, stack))
    case (fm: ZIO.FlatMap[_, _, _, _], v, stack, _) =>
      val newStack: PoorMansFiber.Stack = stack
        .prepended(fm.k.asInstanceOf[Any => IO[Any, Any]])
        .prepended(_ => fm.zio)
      Right((v, newStack))
  }

  val fail: PoorMansFiber.Interpreter = {
    case (f: ZIO.Fail[_, _], _, _, _) =>
      val e = f.fill(() => ZTrace(fiberId = 0L, Nil, Nil, None))
      val exit =
        e.failureOption.map(Exit.fail).orElse(e.dieOption.map(Exit.die)).get
      Left(Some(exit))
  }

  val forkEffectAsync: PoorMansFiber.Interpreter = {
    case (ea: ZIO.EffectAsync[_, _, _], _, stack, fiber) =>
      def callback(vv: ZIO[Any, Any, Any]): Unit =
        fiber.schedule((), stack.prepended(_ => vv))

      ea.register(callback) match {
        case None     => Left(None)
        case Some(io) => Right(((), stack.prepended(_ => io)))
      }
    case (f: ZIO.Fork[_, _, _], v, stack, parent) =>
      val fiber = new PoorMansFiber(parent.interpreter, parent.ec)
      fiber.schedule(v, List(_ => f.value))
      Right((fiber, stack))
  }

  val doYield: PoorMansFiber.Interpreter = {
    case (ZIO.Yield, _, stack, fiber) =>
      fiber.schedule((), stack)
      Left(None)
  }

}
