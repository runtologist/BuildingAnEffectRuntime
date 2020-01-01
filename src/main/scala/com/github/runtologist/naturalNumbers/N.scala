package com.github.runtologist.naturalNumbers

import scala.annotation.tailrec

sealed trait N {
  override def toString(): String = N.stringify(this)
}

object N {

  case object Zero extends N

  case class Cons(n: N) extends N

  @tailrec
  def equals(n: N, m: N): Boolean =
    (n, m) match {
      case (Zero, Zero)         => true
      case (Cons(nn), Cons(mm)) => equals(nn, mm)
      case _                    => false
    }

  // convenience

  @tailrec
  def apply(n: Int, accu: N = Zero): N = {
    if (n < 0) throw new IllegalStateException(s"$n is not a natural number.")
    else if (n == 0) accu
    else apply(n - 1, Cons(accu))
  }

  @tailrec
  def stringify(n: N, accu: Int = 0): String =
    n match {
      case Zero    => accu.toString()
      case Cons(n) => stringify(n, accu + 1)
    }

}
