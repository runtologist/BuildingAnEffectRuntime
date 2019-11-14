package com.github.runtologist.runtime

import com.github.runtologist.runtime.Fiber.Interpreter
import com.github.runtologist.runtime.Fiber.Stack
import zio.Exit
import zio.IO

object FairInterpreter {

  def make(yieldAfter: Long, underlying: Interpreter): Interpreter =
    new Interpreter {
      var count = yieldAfter

      override def apply(
          param: (IO[Any, Any], Any, Stack, Fiber[Any, Any])
      ): Either[
        Option[Exit[Any, Any]], // suspend execution or optionally terminate with an Exit
        (Any, Stack) // or continue with new state and stack
      ] = {
        underlying.apply(param) match {
          case l @ Left(_) => l
          case Right((v, stack)) if count < 1 =>
            count = yieldAfter
            param._4.schedule(v, stack)
            Left(None)
          case r @ Right(_) =>
            count -= 1
            r
        }
      }

      override def isDefinedAt(
          param: (IO[Any, Any], Any, Stack, Fiber[Any, Any])
      ): Boolean = underlying.isDefinedAt(param)
    }
}
