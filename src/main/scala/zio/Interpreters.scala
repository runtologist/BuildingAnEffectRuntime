package zio

import com.github.runtologist.runtime.{Fiber => PoorMansFiber}
import com.github.runtologist.runtime.Fiber.Stack

object Interpreters {

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

  def fairInterpreter(
      yieldAfter: Long,
      underlying: PoorMansFiber.Interpreter
  ): PoorMansFiber.Interpreter =
    new PoorMansFiber.Interpreter {
      var count = yieldAfter
      override def apply(
          param: (IO[Any, Any], Any, Stack, PoorMansFiber[Any, Any])
      ): Either[
        Option[Exit[Any, Any]], // suspend execution or optionally terminate with an Exit
        (Any, Stack) // or continue with new state and stack
      ] = {
        underlying.apply(param) match {
          case l @ Left(_) => l
          case Right((v, stack)) if count < 1 =>
            count = yieldAfter
            param._4.schedule(v, stack)
            Left(None)
          case r @ Right(_) =>
            count -= 1
            r
        }
      }
      override def isDefinedAt(
          param: (IO[Any, Any], Any, Stack, PoorMansFiber[Any, Any])
      ): Boolean = underlying.isDefinedAt(param)
    }

}
