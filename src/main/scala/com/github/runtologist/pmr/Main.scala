package com.github.runtologist.pmr

// import scala.concurrent.ExecutionContext.global

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

  import EffectfulAlgebraErrorsParCoop._

  var r =
    zio.PMR.unsafeRunSync(
      add(
        Cons(Cons(Zero)),
        Cons(Cons(Cons(Zero)))
      )
    )

  println(r)

}
