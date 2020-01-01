package com.github.runtologist.demo

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.runtime.FairInterpreter
import com.github.runtologist.runtime.Runtime
import zio.ZioInterpreters._

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

object DemoAddAllFair extends scala.App {
  val executor = Executors.newFixedThreadPool(1)
  implicit val ec = ExecutionContext.fromExecutor(executor)

  val interpreter =
    FairInterpreter.make(
      yieldAfter = 10,
      succeedFlatMap orElse fail orElse forkEffectAsync orElse doYield
    )

  val runtime = new Runtime(interpreter)
  val r =
    runtime.unsafeRun(
      AddAllCoop
        .addAll(N(2), N(3), N(4), N(5))
        .fork
        .flatMap(_.await)
    )
  println(r)
  executor.shutdown()
}
