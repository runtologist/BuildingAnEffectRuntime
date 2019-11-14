package com.github.runtologist.runtime

import zio.Exit
import zio.{Fiber => ZioFiber}
import zio.IO
import zio.UIO
import zio.ZIO
import zio.internal.OneShot

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.util.Try

class Fiber[E, A](
    val interpreter: Fiber.Interpreter,
    val ec: ExecutionContext
) extends ZioFiber[E, A] {

  @volatile private var result: Option[Exit[E, A]] = None
  @volatile private var interrupted: Boolean = false
  @volatile private var listeners: List[Exit[E, A] => Unit] = Nil

  def register(callback: Exit[E, A] => Unit): Unit =
    listeners ::= callback

  override def interrupt: UIO[Exit[E, A]] = {
    interrupted = true
    await
  }

  @tailrec
  private def step(v: Any, stack: Fiber.Stack): Unit = {
    println(s"step v=$v stackSize=${stack.size}")
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
        println("suspending")
      case Left(Some(exit)) =>
        println(s"done: $exit")
        val typedExit = exit.asInstanceOf[Exit[E, A]]
        result.synchronized {
          result = Some(typedExit)
          listeners.foreach(_(typedExit))
        }
      case Right((v, stack)) =>
        step(v, stack)
    }
  }

  def schedule(v: Any, stack: List[Any => IO[Any, Any]]): Unit =
    ec.execute(() => step(v, stack))

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

class Runtime(interpreter: Fiber.Interpreter) {

  def unsafeRunAsync[E, A](
      io: => IO[E, A]
  )(k: Exit[E, A] => Unit)(implicit ec: ExecutionContext): Unit = {
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
