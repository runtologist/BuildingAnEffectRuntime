package com.github.runtologist.pmr

import zio.UIO

object EffectfulAlgebra {

  trait N
  case object Zero extends N
  case class Cons(n: N) extends N

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

}
