package com.github.runtologist.demo

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.naturalNumbers.N._
import com.github.runtologist.runtime.Runtime
import zio.ZioInterpreters._

import scala.concurrent.ExecutionContext.global

object DemoDiv extends scala.App {
  implicit val ec = global

  val interpreter = succeedFlatMap

  val runtime = new Runtime(interpreter)

  val r =
    runtime.unsafeRun(
      AddMul.add(
        Cons(Cons(Zero)),
        Cons(Cons(Cons(Zero)))
      )
    )
  println(r)

}
