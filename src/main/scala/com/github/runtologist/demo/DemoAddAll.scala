package com.github.runtologist.demo

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.naturalNumbers.N._
import com.github.runtologist.runtime.Runtime
import zio.ZioInterpreters._

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object DemoAddAll extends scala.App {
  val executor = Executors.newSingleThreadScheduledExecutor()
  implicit val ec = ExecutionContext.fromExecutor(executor)

  val interpreter = succeedFlatMap orElse fail orElse forkEffectAsync

  val runtime = new Runtime(interpreter)

  val r =
    runtime.unsafeRun(
      AddAll
        .addAll(
          Cons(Cons(Zero)),
          Cons(Cons(Cons(Zero))),
          Cons(Cons(Cons(Cons(Zero)))),
          Cons(Cons(Cons(Cons(Cons(Zero)))))
        )
    )
  println(r)
  executor.shutdown()
}
