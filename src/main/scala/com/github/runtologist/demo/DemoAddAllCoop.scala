package com.github.runtologist.demo

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.naturalNumbers.N._
import com.github.runtologist.runtime.Runtime
import zio.ZioInterpreters._

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

object DemoAddAllCoop extends scala.App {
  val executor = Executors.newSingleThreadScheduledExecutor()
  implicit val ec = ExecutionContext.fromExecutor(executor)

  val interpreter =
    succeedFlatMap orElse fail orElse forkEffectAsync orElse doYield

  val runtime = new Runtime(interpreter)

  val r =
    runtime.unsafeRun(
      AddAllCoop
        .addAll(
          Cons(Cons(Zero)),
          Cons(Cons(Cons(Zero))),
          Cons(Cons(Cons(Cons(Zero)))),
          Cons(Cons(Cons(Cons(Cons(Zero)))))
        )
        .fork
        .flatMap(_.await)
    )
  println(r)
  executor.shutdown()

}
