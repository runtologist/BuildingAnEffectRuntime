package com.github.runtologist.demo

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.runtime.Runtime
import zio.ZioInterpreters._

import scala.concurrent.ExecutionContext.global

object DemoAdd extends scala.App {
  implicit val ec = global

  val interpreter = succeedFlatMap

  val runtime = new Runtime(interpreter)

  val r =
    runtime.unsafeRun(
      AddMul.add(N(2), N(3))
    )
  println(r)

}
