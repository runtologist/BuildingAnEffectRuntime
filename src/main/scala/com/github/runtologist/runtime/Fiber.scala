package com.github.runtologist.runtime

import zio.Exit
import zio.{Fiber => ZioFiber}
import zio.IO
import zio.UIO

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.Random

object Fiber {

  type Stack = List[Any => IO[Any, Any]]

  type InterpreterParams =
    (
        IO[Any, Any], // the next IO to interpret
        Any, // the current input parameter
        Stack, // the remainder of the stack
        Fiber[Any, Any] // the current fiber
    )
  type Interpretation =
    Either[
      Option[Exit[Any, Any]], // suspend execution (None) or terminate with an Exit
      (Any, Stack) // or continue with new state and stack
    ]

  // sealed trait Int2
  // case class Exit_(x: Exit[Any, Any]) extends Int2
  // case class Step(v: Any, stack: Stack) extends Int2
  // case object Suspend extends Int2

  type Interpreter =
    PartialFunction[ // may interpret just part of the ADT
      InterpreterParams,
      Interpretation
    ]

  val notImplemented: Interpreter = {
    case (other, _, _, _) =>
      val e = new IllegalStateException(s"not implemented: ${other.getClass}")
      Left(Some(Exit.die(e)))
  }

}

class Fiber[E, A](
    val interpreter: Fiber.Interpreter,
    val ec: ExecutionContext
) extends ZioFiber[E, A] {

  val id: Int = Random.nextInt(Int.MaxValue)

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
    val indent = fansi.Color.all(id % 16)(s"$id: " + ".".*(stack.length))
    println(s"$indent step $v")
    val next =
      for {
        _ <- Either.cond(!interrupted, (), Some(Exit.interrupt))
        f <- stack.headOption.toRight(Some(Exit.succeed(v.asInstanceOf[A])))
        io <- Try(f(v)).toEither.left.map(e => Some(Exit.die(e)))
        _ = println(
          s"$indent interpreting " + fansi.Color.all(io.tag + 5)(
            io.getClass().getSimpleName()
          )
        )
        next <- interpreter.applyOrElse(
          (io, v, stack.tail, this.asInstanceOf[Fiber[Any, Any]]),
          Fiber.notImplemented
        )
      } yield next
    next match {
      case Left(None) =>
        println(s"$indent suspending")
      case Left(Some(exit)) =>
        println(s"$indent done: $exit")
        val typedExit = exit.asInstanceOf[Exit[E, A]]
        result.synchronized {
          result = Some(typedExit)
          listeners.foreach(_(typedExit))
        }
      case Right((v, stack)) =>
        step(v, stack)
    }
  }

  def schedule(v: Any, stack: Fiber.Stack): Unit =
    ec.execute(() => step(v, stack))

  // implement Fiber trait

  override def await: UIO[Exit[E, A]] =
    UIO.effectAsyncMaybe { k =>
      result.synchronized {
        result.fold[Option[UIO[Exit[E, A]]]] {
          register(exit => k(UIO.succeed(exit)))
          None
        }(r => Some(UIO.succeed(r)))
      }
    }

  override def poll: UIO[Option[Exit[E, A]]] = UIO.succeed(result)

  // not implemented
  override def inheritFiberRefs: UIO[Unit] = UIO.unit
}
