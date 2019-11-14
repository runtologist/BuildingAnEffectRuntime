package com.github.runtologist.naturalNumbers

import com.github.runtologist.naturalNumbers.N._

object UnsafeAddMul {

  def add(n: N, m: N): N =
    m match {
      case Zero     => n
      case Cons(mm) => Cons(add(n, mm))
    }

  def mul(n: N, m: N): N =
    m match {
      case Zero     => Zero
      case Cons(mm) => add(mul(n, mm), n)
    }

}
