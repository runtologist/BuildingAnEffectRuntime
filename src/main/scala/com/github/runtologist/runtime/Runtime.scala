package com.github.runtologist.runtime

import zio.Exit
import zio.IO
import zio.internal.OneShot

import scala.concurrent.ExecutionContext

class Runtime(interpreter: Fiber.Interpreter)(implicit ec: ExecutionContext) {

  def unsafeRunAsync[E, A](io: => IO[E, A])(k: Exit[E, A] => Unit): Unit = {
    val fiber = new Fiber(interpreter, ec)
    fiber.register(k)
    fiber.schedule(io, List(_ => io))
  }

  def unsafeRun[E, A](io: => IO[E, A]): Exit[E, A] = {
    val oneShot = OneShot.make[Exit[E, A]]
    unsafeRunAsync(io)(oneShot.set)
    oneShot.get()
  }

}
