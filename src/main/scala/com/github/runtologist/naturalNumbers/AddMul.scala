package com.github.runtologist.naturalNumbers

import com.github.runtologist.naturalNumbers.N._

import zio.UIO

object AddMul {

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
