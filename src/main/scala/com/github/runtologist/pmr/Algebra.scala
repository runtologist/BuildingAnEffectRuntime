package com.github.runtologist.pmr

import scala.annotation.tailrec

object Algebra {

  trait N
  case object Zero extends N
  case class Cons(n: N) extends N

  @tailrec
  def equals(n: N, m: N): Boolean =
    (n, m) match {
      case (Zero, Zero)         => true
      case (Cons(nn), Cons(mm)) => equals(nn, mm)
      case _                    => false
    }

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
