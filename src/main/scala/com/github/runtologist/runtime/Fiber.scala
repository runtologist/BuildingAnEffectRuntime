package com.github.runtologist.runtime

import zio.Exit
import zio.{Fiber => ZioFiber}
import zio.Interpreters
import zio.IO
import zio.ZIO

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.util.Try
import zio.UIO
import zio.internal.OneShot

class Fiber[E, A](
    val interpreter: Fiber.Interpreter,
    val ec: ExecutionContext,
    val yieldIn: Long = 10
) extends ZioFiber[E, A] {

  @volatile private var result: Option[Exit[E, A]] = None
  @volatile private var interrupted: Boolean = false
  @volatile private var listeners: List[Exit[E, A] => Unit] = Nil

  def register(callback: Exit[E, A] => Unit): Unit =
    listeners ::= callback

  override def interrupt: UIO[Exit[E, A]] = {
    println("set interrupted true")
    interrupted = true
    await
  }

  @tailrec
  private def step(
      v: Any,
      stack: List[Any => IO[Any, Any]]
  ): Unit = {
    println(s"step $v ${stack.size}")
    val next =
      for {
        _ <- Either.cond(!interrupted, (), Some(Exit.interrupt))
        f <- stack.headOption.toRight(Some(Exit.succeed(v.asInstanceOf[A])))
        io <- Try(f(v)).toEither.left.map(e => Some(Exit.die(e)))
        next <- interpreter.applyOrElse(
          (io, v, stack.tail, this.asInstanceOf[Fiber[Any, Any]]),
          Fiber.notImplemented
        )
      } yield next
    next match {
      case Left(None) =>
        println("suspended")
      case Left(Some(exit)) =>
        println(s"done: $exit")
        val typedExit = exit.asInstanceOf[Exit[E, A]]
        result.synchronized {
          result = Some(typedExit)
          listeners.foreach(_(typedExit))
        }
      case Right((v, stack)) =>
        println("next")
        step(v, stack)
    }
  }

  def schedule(v: Any, stack: List[Any => IO[Any, Any]]): Unit = {
    ec.execute(() => step(v, stack))
  }

  // implement Fiber trait

  override def await: UIO[Exit[E, A]] =
    ZIO.effectAsyncMaybe[Any, Nothing, Exit[E, A]] { k =>
      result.synchronized {
        result.fold[Option[UIO[Exit[E, A]]]] {
          register(exit => k(UIO.succeed(exit)))
          None
        }(r => Some(ZIO.succeed(r)))
      }
    }

  override def poll: UIO[Option[Exit[E, A]]] = UIO.succeed(result)

  // not implemented
  override def inheritFiberRefs: UIO[Unit] = UIO.unit
}

object Fiber {

  type Stack = List[Any => IO[Any, Any]]

  type Interpreter =
    PartialFunction[ // maybe incomplete
      (IO[Any, Any], Any, Stack, Fiber[Any, Any]),
      Either[
        Option[Exit[Any, Any]], // suspend execution or optionally terminate with an Exit
        (Any, Stack) // or continue with new state and stack
      ]
    ]

  def notImplemented: Interpreter = {
    case (other, _, _, _) =>
      val e = new IllegalStateException(s"not implemented: ${other.getClass}")
      Left(Some(Exit.die(e)))
  }
}

object RuntimeSucceedFlatMap {

  def unsafeRunAsync[E, A](
      io: => IO[E, A]
  )(k: Exit[E, A] => Unit)(implicit ec: ExecutionContext): Unit = {
    val fiber = new Fiber(Interpreters.succeedFlatMap, ec)
    fiber.register(k)
    fiber.schedule(io, List(_ => io))
  }

}

object FullRuntime {

  def unsafeRunAsync[E, A](
      io: => IO[E, A]
  )(k: Exit[E, A] => Unit)(implicit ec: ExecutionContext): Unit = {
    import zio.Interpreters._
    val interpreter =
      fairInterpreter(
        yieldAfter = 10,
        succeedFlatMap orElse fail orElse forkEffectAsync orElse doYield
      )
    val fiber = new Fiber(interpreter, ec)
    fiber.register(k)
    fiber.schedule(io, List(_ => io))
  }

  def unsafeRun[E, A](
      io: => IO[E, A]
  )(implicit ec: ExecutionContext): Exit[E, A] = {
    val oneShot = OneShot.make[Exit[E, A]]
    unsafeRunAsync(io)(oneShot.set)
    oneShot.get()
  }

}
