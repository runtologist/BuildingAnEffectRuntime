package com.github.runtologist.naturalNumbers

import org.scalatest.funsuite.AnyFunSuite
import com.github.runtologist.naturalNumbers.N.Cons
import com.github.runtologist.naturalNumbers.N.Zero
import scala.util.Try

class NTest extends AnyFunSuite {

  test("apply happy path") {
    assert(N(3) === Cons(Cons(Cons(Zero))))
  }

  test("apply negative") {
    assert(Try(N(-3)).isFailure)
  }

  test("toString") {
    assert(N(3).toString === "3")
  }

}
