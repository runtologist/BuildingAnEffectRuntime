package com.github.runtologist.runtime

import com.github.runtologist.runtime.Fiber._

import zio.Exit
import zio.{Fiber => ZioFiber}
import zio.IO
import zio.UIO

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.util.Try
import zio.FiberRef
import zio.ZTrace
import zio.Fiber.Descriptor

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

  private[this] var nextSeqNo = 0L
  def nextId(): ZioFiber.Id = {
    val next = nextSeqNo
    nextSeqNo += 1
    ZioFiber.Id(System.currentTimeMillis, next)
  }

}

class Fiber[E, A](
    val interpreter: Fiber.Interpreter,
    val ec: ExecutionContext,
    val printDebug: Boolean = false
) extends ZioFiber[E, A] {

  val _id: ZioFiber.Id = nextId()
  override def id: zio.UIO[Option[zio.Fiber.Id]] = UIO.some(_id)

  @volatile private var result: Option[Exit[E, A]] = None
  @volatile private var interrupted: Set[ZioFiber.Id] = Set.empty
  @volatile private var listeners: List[Exit[E, A] => Unit] = Nil

  def register(callback: Exit[E, A] => Unit): Unit =
    listeners ::= callback

  override def interruptAs(fiberId: ZioFiber.Id): UIO[Exit[E, A]] = {
    interrupted += fiberId
    await
  }

  private def debug(msg: String, stack: Stack): Unit = if (printDebug) {
    val indent =
      fansi.Color.all(_id.seqNumber.toInt + 3 % 16)(
        s"${_id.seqNumber}: " + ".".*(stack.length)
      )
    println(s"$indent $msg")
  }

  @tailrec
  private def step(v: Any, stack: Fiber.Stack): Unit = {

    debug(s"step $v", stack)
    val safeInterpretation: Interpretation =
      if (interrupted.nonEmpty) {
        Return(Exit.interrupt(interrupted.head))
      } else {
        stack match {
          case Nil => Return(Exit.succeed(v.asInstanceOf[A]))
          case f :: tail =>
            Try(f(v)).fold(
              e => Return(Exit.die(e)),
              io => {
                debug(
                  s"interpreting " + fansi.Color.all(io.tag + 5)(
                    io.getClass().getSimpleName()
                  ),
                  stack
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
        debug("suspending", stack)
      case Return(exit) =>
        debug(s"done: $exit", stack)
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

  def descriptor: Descriptor =
    Descriptor(
      _id,
      zio.Fiber.Status.Done,
      interrupted,
      zio.InterruptStatus.Interruptible,
      UIO.succeed(Iterable.empty),
      null
    )

  override def poll: UIO[Option[Exit[E, A]]] = UIO.succeed(result)

  val _trace: ZTrace = ZTrace(_id, Nil, Nil, None)
  override def trace: zio.UIO[Option[ZTrace]] = UIO.some(_trace)

  override def inheritRefs: UIO[Unit] = UIO.unit

  override def children: zio.UIO[Iterable[ZioFiber[Any, Any]]] = UIO.never

  override def getRef[T](ref: FiberRef[T]): zio.UIO[T] = UIO.never

  override def status: zio.UIO[zio.Fiber.Status] = UIO.never

}
