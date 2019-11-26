package com.github.runtologist.runtime

import com.github.runtologist.runtime.Fiber._

import zio.Exit
import zio.{Fiber => ZioFiber}
import zio.IO
import zio.UIO

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.util.Try

object Fiber {

  type Stack = List[Any => IO[Any, Any]]

  type InterpreterParams =
    (
        IO[Any, Any], // the next IO to interpret
        Any, // the current input parameter
        Stack, // the remainder of the stack
        Fiber[Any, Any] // the current fiber
    )

  sealed trait Interpretation
  case object Suspend extends Interpretation
  case class Return(exit: Exit[Any, Any]) extends Interpretation
  case class Step(v: Any, stack: Stack) extends Interpretation

  type Interpreter =
    PartialFunction[ // may interpret just part of the ADT
      InterpreterParams,
      Interpretation
    ]

  val notImplemented: Interpreter = {
    case (other, _, _, _) =>
      val e = new IllegalStateException(s"not implemented: ${other.getClass}")
      Return(Exit.die(e))
  }

  private[this] var nextId_ = 0
  def nextId(): Int = {
    val next = nextId_
    nextId_ += 1
    next
  }

}

class Fiber[E, A](
    val interpreter: Fiber.Interpreter,
    val ec: ExecutionContext
) extends ZioFiber[E, A] {

  val id: Int = nextId()

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
    val indent = fansi.Color.all(id + 3 % 16)(s"$id: " + ".".*(stack.length))
    println(s"$indent step $v")
    val safeInterpretation: Interpretation =
      if (interrupted) {
        Return(Exit.interrupt)
      } else {
        stack match {
          case Nil => Return(Exit.succeed(v.asInstanceOf[A]))
          case f :: tail =>
            Try(f(v)).fold(
              e => Return(Exit.die(e)),
              io => {
                println(
                  s"$indent interpreting " + fansi.Color.all(io.tag + 5)(
                    io.getClass().getSimpleName()
                  )
                )
                interpreter.applyOrElse(
                  (io, v, tail, this.asInstanceOf[Fiber[Any, Any]]),
                  Fiber.notImplemented
                )
              }
            )
        }
      }
    safeInterpretation match {
      case Suspend =>
        println(s"$indent suspending")
      case Return(exit) =>
        println(s"$indent done: $exit")
        val typedExit = exit.asInstanceOf[Exit[E, A]]
        result.synchronized {
          result = Some(typedExit)
          listeners.foreach(_(typedExit))
        }
      case Step(v, stack) =>
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
