package com.github.runtologist.runtime

import com.github.runtologist.naturalNumbers._
import com.github.runtologist.naturalNumbers.N._

import scala.concurrent.ExecutionContext.global

// object Main extends scala.App {

//   implicit val ec = global

//   import EffectfulAlgebraErrorsParCoop._

//   var r =
//     zio.PMRErrorsParCoopFairInterruption.unsafeRunSync(
//       addAll(
//         Cons(Cons(Zero)),
//         Cons(Cons(Cons(Zero))),
//         Cons(Cons(Cons(Cons(Zero)))),
//         Cons(Cons(Cons(Cons(Cons(Zero)))))
//       ).fork.flatMap(_.interrupt)
//     )

//   println(r)

// }
object Main extends scala.App {
  implicit val ec = global
  val r =
    FullRuntime.unsafeRun(
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
