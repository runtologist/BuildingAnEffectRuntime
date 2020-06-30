package com.github.runtologist.naturalNumbers

import scala.annotation.tailrec
import zio.UIO
import zio.IO
import zio.ZIO

sealed trait N {
  override def toString(): String = N.stringify(this)
}

object N extends NOps {

  case object Zero extends N

  case class Cons(n: N) extends N

  def apply(n: Int): N = fromInt(n)

  @tailrec
  def fromInt(n: Int, accu: N = Zero): N = {
    if (n < 0) throw new IllegalStateException(s"$n is not a natural number.")
    else if (n == 0) accu
    else fromInt(n - 1, Cons(accu))
  }

  def stringify(n: N): String = {

    @tailrec
    def stringifyInternal(n: N, accu: Int): String =
      n match {
        case Zero    => accu.toString()
        case Cons(n) => stringifyInternal(n, accu + 1)
      }

    stringifyInternal(n, 0)
  }
}

sealed trait Err

object Err {
  case object Div0 extends Err
  case object Neg extends Err
}

trait NOps {

  import N._
  import Err._

  @tailrec
  final def equals(n: N, m: N): Boolean =
    (n, m) match {
      case (Zero, Zero)         => true
      case (Cons(nn), Cons(mm)) => equals(nn, mm)
      case _                    => false
    }

  final def plus(n: N, m: N): UIO[N] = {
    m match {
      case Zero     => UIO.succeed(n)
      case Cons(mm) => UIO.unit *> plus(n, mm).map(Cons)
    }
  }

  final def times(n: N, m: N): UIO[N] =
    m match {
      case Zero     => UIO.succeed(Zero)
      case Cons(mm) => UIO.unit *> times(n, mm).flatMap(o => plus(o, n))
    }

  @tailrec
  final def minus(n: N, m: N): IO[Neg.type, N] =
    (n, m) match {
      case (_, Zero)            => IO.succeed(n)
      case (Zero, _)            => IO.fail(Neg)
      case (Cons(nn), Cons(mm)) => minus(nn, mm)
    }

  final def div(n: N, m: N): IO[Div0.type, N] = {

    def step(accu: N, nn: N): UIO[N] = {
      if (nn == Zero)
        UIO.succeed(accu)
      else
        minus(nn, m)
          .foldM(
            { case Neg => UIO.succeed(accu) },
            rest => step(Cons(accu), rest)
          )
    }

    for {
      _ <- IO.fail(Div0).when(m == Zero)
      r <- step(Zero, n)
    } yield r
  }

  final def addAll(ns: N*): UIO[N] =
    ns.toList match {
      case Nil      => UIO.succeed(Zero)
      case n :: Nil => UIO.succeed(n)
      case list =>
        val (l, r) = list.splitAt(ns.length / 2)
        for {
          lsf <- addAll(l: _*).fork
          rsf <- addAll(r: _*).fork
          ls <- lsf.join
          rs <- rsf.join
          s <- plus(ls, rs)
        } yield s
    }

  final def addAllCoop(ns: N*): UIO[N] =
    ns.toList match {
      case Nil      => UIO.succeed(Zero)
      case n :: Nil => UIO.succeed(n)
      case list =>
        val (l, r) = list.splitAt(ns.length / 2)
        for {
          lsf <- addAll(l: _*).fork
          rsf <- addAll(r: _*).fork
          ls <- lsf.join
          rs <- rsf.join
          _ <- ZIO.yieldNow
          s <- plus(ls, rs)
        } yield s

    }

}
