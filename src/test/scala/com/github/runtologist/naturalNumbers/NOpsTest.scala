package com.github.runtologist.naturalNumbers

import com.github.runtologist.runtime.Runtime
import zio.Exit
import Err._
import zio.ZioInterpreters
import zio.ZioInterpreters._
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import com.github.runtologist.runtime.FairInterpreter
import org.scalatest.funsuite.AnyFunSuite
import zio.IO
import org.scalatest.concurrent.Eventually
import org.scalatest.time.Span
import org.scalatest.time.Second

class NOpsTest extends AnyFunSuite with Eventually {

  override implicit val patienceConfig = PatienceConfig(Span(1, Second))

  val executor = Executors.newFixedThreadPool(1)
  implicit val ec = ExecutionContext.fromExecutor(executor)

  val interpreter =
    FairInterpreter.make(
      yieldAfter = 10,
      succeedFlatMap orElse ZioInterpreters.failFold orElse forkEffectAsync orElse doYield orElse descriptor
    )

  val runtime = new Runtime(interpreter)

  test("plus") {
    val r = runtime.unsafeRun(N.plus(N(2), N(3)))
    assert(r === Exit.Success(N(5)))
  }

  test("plus is stack safe") {
    val r = runtime.unsafeRun(N.plus(N(10000), N(10000)))
    assert(r === Exit.Success(N(20000)))
  }

  test("times") {
    val r = runtime.unsafeRun(N.times(N(2), N(3)))
    assert(r === Exit.Success(N(6)))
  }

  test("minus happy path") {
    val r = runtime.unsafeRun(N.minus(N(3), N(2)))
    assert(r === Exit.Success(N(1)))
  }

  test("minus negative") {
    val r = runtime.unsafeRun(N.minus(N(2), N(3)))
    assert(r === Exit.fail(Neg))
  }

  test("div happy path") {
    val r = runtime.unsafeRun(N.div(N(6), N(2)))
    assert(r === Exit.Success(N(3)))
  }

  test("div Zero") {
    val r = runtime.unsafeRun(N.div(N(2), N(0)))
    assert(r === Exit.fail(Div0))
  }

  test("addAll") {
    val r = runtime.unsafeRun(N.addAll(N(2), N(3), N(4), N(5)))
    assert(r === Exit.Success(N(14)))
  }

  test("addAllCoop") {
    val r = runtime.unsafeRun(N.addAllCoop(N(2), N(3), N(4), N(5)))
    assert(r === Exit.Success(N(14)))
  }

  test("yield") {
    var x = false
    var r = Option.empty[String]
    val io =
      for {
        f1 <- (IO.unit *> IO.yieldNow).forever.fork
        f2 <- IO.effectTotal[Any]({ x = true }).flatMap(_ => f1.interrupt).fork
        _ <- f2.await
        _ <- f1.await
      } yield r = Some("terminated!")
    runtime.unsafeRunAsync(io)(_ => ())
    eventually(assert(x === true && r === Some("terminated!")))
  }

  test("fairness") {
    var x = false
    var r = Option.empty[String]
    val io =
      for {
        f1 <- IO.unit.forever.fork
        f2 <- IO.effectTotal[Any]({ x = true }).flatMap(_ => f1.interrupt).fork
        _ <- f2.await
        _ <- f1.await
      } yield r = Some("terminated!")
    runtime.unsafeRunAsync(io)(_ => ())
    eventually(assert(x === true && r === Some("terminated!")))
  }

}
