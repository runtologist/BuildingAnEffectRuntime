package com.github.runtologist.naturalNumbers

import com.github.runtologist.naturalNumbers.Err._
import com.github.runtologist.naturalNumbers.N._

import zio.IO
import zio.UIO

object SubDiv {

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
