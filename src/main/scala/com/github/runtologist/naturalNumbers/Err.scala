package com.github.runtologist.naturalNumbers

sealed trait Err

object Err {
  case object Div0 extends Err
  case object Neg extends Err
}
