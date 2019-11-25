package com.github.runtologist.runtime

import com.github.runtologist.runtime.Fiber._
import zio.IO

object FairInterpreter {

  def make(yieldAfter: Long, underlying: Interpreter): Interpreter =
    new Interpreter {
      var count = yieldAfter

      override def apply(
          param: (IO[Any, Any], Any, Stack, Fiber[Any, Any])
      ): Interpretation = {
        underlying.apply(param) match {
          case Suspend       => Suspend
          case r @ Return(_) => r
          case Step(v, stack) if count < 1 =>
            val fiber = param._4
            println(s"Suspending Fiber ${fiber.id} to be fair.")
            count = yieldAfter
            fiber.schedule(v, stack)
            Suspend
          case s @ Step(_, _) =>
            count -= 1
            s
        }
      }

      override def isDefinedAt(
          param: (IO[Any, Any], Any, Stack, Fiber[Any, Any])
      ): Boolean = underlying.isDefinedAt(param)
    }
}
