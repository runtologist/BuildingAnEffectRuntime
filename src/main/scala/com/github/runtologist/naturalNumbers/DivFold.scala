package com.github.runtologist.naturalNumbers

import com.github.runtologist.naturalNumbers.Err._
import com.github.runtologist.naturalNumbers.N._

import zio.IO
import zio.UIO

object DivFold {

  def div(n: N, m: N): IO[Div0.type, N] =
    if (m == Zero) IO.fail(Div0)
    else {
      def step(accu: N, nn: N): UIO[N] = {
        if (nn == Zero)
          UIO.succeed(accu)
        else
          SubDiv
            .sub(nn, m)
            .foldM(_ => UIO.succeed(accu), rest => step(Cons(accu), rest))
      }
      step(Zero, n)
    }

}
