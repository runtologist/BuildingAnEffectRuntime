package com.github.runtologist.naturalNumbers

import scala.annotation.tailrec

sealed trait N

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

}
