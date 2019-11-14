package com.github.runtologist.runtime

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.naturalNumbers.N._
import zio.ZioInterpreters._

import scala.concurrent.ExecutionContext.global

object Main extends scala.App {
  implicit val ec = global

  val interpreter =
    FairInterpreter.make(
      yieldAfter = 10,
      succeedFlatMap orElse fail orElse forkEffectAsync orElse doYield
    )

  val runtime = new Runtime(interpreter)

  val r =
    runtime.unsafeRun(
      // AddMul.add(
      //   Cons(Cons(Zero)),
      //   Cons(Cons(Cons(Zero)))
      // )
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

}
