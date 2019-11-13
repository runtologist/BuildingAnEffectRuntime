package com.github.runtologist.pmr

import zio.IO
import zio.UIO

object EffectfulAlgebraErrors {

  sealed trait N
  case object Zero extends N
  case class Cons(n: N) extends N

  sealed trait Err
  case object Div0 extends Err
  case object Neg extends Err

  def equals(n: N, m: N): UIO[Boolean] =
    (n, m) match {
      case (Zero, Zero)         => UIO.succeed(true)
      case (Cons(nn), Cons(mm)) => equals(nn, mm)
      case _                    => UIO.succeed(false)
    }

  def add(n: N, m: N): UIO[N] =
    m match {
      case Zero     => UIO.succeed(n)
      case Cons(mm) => add(n, mm).map(Cons)
    }

  def mul(n: N, m: N): UIO[N] =
    m match {
      case Zero     => UIO.succeed(Zero)
      case Cons(mm) => mul(n, mm).flatMap(o => add(o, n))
    }

  def sub(n: N, m: N): IO[Neg.type, N] =
    (n, m) match {
      case (_, Zero)            => IO.succeed(n)
      case (Zero, _)            => IO.fail(Neg)
      case (Cons(nn), Cons(mm)) => sub(nn, mm)
    }

  def div(n: N, m: N): IO[Div0.type, N] =
    if (n == Zero) IO.succeed(Zero)
    else if (m == Zero) IO.fail(Div0)
    else {
      def step(accu: N, nn: N, mm: N): UIO[N] = {
        (nn, mm) match {
          case (Zero, _)              => UIO.succeed(accu)
          case (Cons(nnn), Zero)      => step(Cons(accu), nnn, m)
          case (Cons(nnn), Cons(mmm)) => step(accu, nnn, mmm)
        }
      }
      step(Zero, n, m)
    }

}
