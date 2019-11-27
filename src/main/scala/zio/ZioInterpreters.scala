package zio

import com.github.runtologist.runtime.{Fiber => PoorMansFiber}
import com.github.runtologist.runtime.Fiber._
import scala.util.Try

object ZioInterpreters {

  type IOFn = Any => IO[Any, Any]

  val succeedFlatMap: PoorMansFiber.Interpreter = {
    case (s: ZIO.Succeed[_], _, stack, _) => Step(s.value, stack)
    case (fm: ZIO.FlatMap[_, _, _, _], v, stack, _) =>
      val newStack: PoorMansFiber.Stack = stack
        .prepended(fm.k.asInstanceOf[IOFn])
        .prepended(_ => fm.zio)
      Step(v, newStack)
  }

  val fail: PoorMansFiber.Interpreter = {
    case (f: ZIO.Fail[_, _], _, _, fiber) =>
      val e = f.fill(() => ZTrace(fiberId = fiber.id, Nil, Nil, None))
      val exit = Exit.halt(e)
      Return(exit)
  }

  val forkEffectAsync: PoorMansFiber.Interpreter = {
    case (ea: ZIO.EffectAsync[_, _, _], _, stack, fiber) =>
      def callback(vv: ZIO[Any, Any, Any]): Unit =
        fiber.schedule((), stack.prepended(_ => vv))

      ea.register(callback) match {
        case None     => Suspend
        case Some(io) => Step((), stack.prepended(_ => io))
      }
    case (f: ZIO.Fork[_, _, _], v, stack, parent) =>
      val fiber = new PoorMansFiber(parent.interpreter, parent.ec)
      fiber.schedule(v, List(_ => f.value))
      Step(fiber, stack)
  }

  val doYield: PoorMansFiber.Interpreter = {
    case (ZIO.Yield, _, stack, fiber) =>
      fiber.schedule((), stack)
      Suspend
  }

  // Do not mix with fail!
  val failFold: PoorMansFiber.Interpreter = {
    case (fold: ZIO.Fold[_, _, _, _, _], v, stack, _) =>
      // Fold implements Function1 and apply aliases success. By pushing fold,
      // we can directly proceed on the happy path, but keep the full Fold for
      // the error case.
      val newStack =
        stack
          .prepended(fold.asInstanceOf[IOFn])
          .prepended((_: Any) => fold.value.asInstanceOf[IO[Any, Any]])
      Step(v, newStack)
    case (fail: ZIO.Fail[_, _], _, stack, fiber) =>
      val cause = fail.fill(() => ZTrace(fiberId = fiber.id, Nil, Nil, None))
      val tailWithFold =
        stack.dropWhile(f => !f.isInstanceOf[ZIO.Fold[_, _, _, _, _]])
      tailWithFold match {
        case (handler: ZIO.Fold[_, _, _, _, _]) :: tail =>
          val newStack = tail.prepended(handler.failure.asInstanceOf[IOFn])
          Step(cause, newStack)
        case _ =>
          val exit = Exit.halt(cause)
          Return(exit)
      }
  }

  val effect: PoorMansFiber.Interpreter = {
    case (et: ZIO.EffectTotal[_], _, stack, _) =>
      Step(et.effect(), stack)
    case (ep: ZIO.EffectPartial[_], _, stack, _) =>
      Try(ep.effect()).fold(
        e => Return(Exit.fail(e)),
        a => Step(a, stack)
      )
  }

}
