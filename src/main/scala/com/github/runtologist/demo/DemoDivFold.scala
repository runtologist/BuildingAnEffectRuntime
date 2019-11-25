package com.github.runtologist.demo

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.naturalNumbers.N._
import com.github.runtologist.runtime.Runtime
import zio.ZioInterpreters._

import scala.concurrent.ExecutionContext.global
import zio.UIO

object DemoDivFold extends scala.App {
  implicit val ec = global

  val interpreter = succeedFlatMap orElse failFold

  val runtime = new Runtime(interpreter)

  val r =
    runtime.unsafeRun(
      DivFold
        .div(
          Cons(Cons(Zero)),
          Zero
        )
        .flatMap(r => UIO.succeed(r.toString))
        .catchAll(_ => UIO.succeed("Oh noes!"))
    )
  println(r)

}
