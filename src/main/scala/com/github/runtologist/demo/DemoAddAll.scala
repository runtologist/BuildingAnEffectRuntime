package com.github.runtologist.demo

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.naturalNumbers.N._
import com.github.runtologist.runtime.Runtime
import zio.ZioInterpreters._

import scala.concurrent.ExecutionContext.global

object DemoAddAll extends scala.App {
  implicit val ec = global

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

}
