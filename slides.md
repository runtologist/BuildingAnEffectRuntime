---
title: Build yourself an effect system
theme: night
highlightTheme: hybrid
css: css/fullscreen.css
#width: '100%'
#height: '100%'
revealOptions:
  transition: 'fade'
  center: false
  slideNumber: true
  hash: true
---

## Build yourself an effect system

---

### Simon Schenk

<img  src="img/icon__filled--light@3x.png" style="max-width:20%"> 

CTO at Risk42 (https://risk42.com)

 ----

###### https://github.com/zio/interop-reactive-streams

###### https://github.com/zio/telemetry

 ----

simon@risk42.com - @runtologist

---

### Why ZIO?

"Read an `R` from the environment and run an effect returning a result of `A` or an Error of `E`."

#### cats
```scala
import lots.of.implicits

Kleisli[F[_], R, Either[E, A]]
```

#### ZIO
```scala
ZIO[R, E, A]
```

---

### Why this talk?

* 1kloc Runtime
* complex but not complicated
* Strip away non essentials
* prefer readability over performance
* On the slides: simplified types, might not compile

---

### Agenda

* Functional Effects, Errors vs. Defects
* Running Effects
* Example Domain: Natural Numbers 
* Build Fiber runtime driving Interpreters
* Implement Interpreters
  * syncronous happy path
  ---
  * error channel
  * asynchronicity
  * cooperative and fair scheduling
  * error recovery

---

### Functional Effects

Immutable *value* that *models side-effects*

```scala
// does nothing, just describes writing to console, can't fail
val effect: IO[Nothing, Unit] = 
  IO(println("What's your name?"))
```

---

### vs Future

```scala
// does nothing, just describes writing to console, can't fail
val effect: IO[Nothing, Unit] = 
  IO(println("What's your name?"))

// immediately starts executing, maybe fails?
val future: Future[Unit] = 
  Future(println("What's your name?"))
```

---

### Rich Combinators

```scala
// still does nothing
val readName: IO[IOError, String] = 
  for {
    _    <- IO(println("What's your name?"))
    name <- Task(console.readLine)
              .refineOrDie { case e: IOError => e }
  } yield name

// still nothing
val validName: IO[Nothing, String] =
  readName
    .eventually
    .repeat(Schedule.doUntil(_.length > 3))

// and still nothing
val program: IO[Nothing, Unit] =
  validName.flatMap(name => IO(println(s"Hello, $name!")))
```

---

### At the end of the world...

effects must be run in order to do anything 

```scala
val program: IO[Nothing, Unit] =
  validName.flatMap(name => IO(println(s"Hello, $name!")))

// finally do something
runtime.unsafeRun(program)
```

```shell
What's your name?
Foo
What's your name?
FooBar
Hello, FooBar!
```

---

### Running Effects

* Execute in a *Fiber*
* Asynchronously
* Can fork more Fibers

```scala
val effect =
  for {
    _      <- console.putStrLn("What's your name?")
    fiber  <- expensiveBackgroundWork.fork
    name   <- console.readStrLn
    _      <- console.putStrLn(s"Hello, $name!")
    result <- fiber.join
  } yield result

runtime.unsafeRun(effect)
```

---

### Fiber = Lightweight thread implemented in Library

* Like thread:
  * Will run in parallel. 
  * fork, join/await, interrupt
* Unlike Thread:
  * No mapping to OS level threads, works on ScalaJS
  * Cheap. Run 10s of 1000s concurrently.
  * Fine grained control, extensible

---

### Concurrency != Parallelism

> Concurrency is the composition of independently executing processes, while parallelism is the simultaneous execution of (possibly related) computations.

###### https://howtodoinjava.com/java/multi-threading/concurrency-vs-parallelism/


---

### Errors

Expected Failure Modes

```scala
trait NameError
case object TooShort extends NameError

val readName: IO[NameError, String] = 
  for {
    _    <- IO(println("What's your name?"))
    name <- Task(console.readLine)
              .refineOrDie { case e: IOError => e }
              .catchAll(_ => "")
    _    <- IO.fail(TooShort).when(name.length < 3)
  } yield name

val r: Exit[NameError, String] = 
  runtime.unsafeRunSync(readName)
```

---

### vs Defects

<img src="img/bad-bug.svg" style="width:50%"/>

---

### Interruption

```scala
def race[E, A](io1: IO[E, A], io2: IO[E, A]): IO[E, A] =
  for {
    p <- Promise.make
    fiber1 <- p.complete(io1).fork
    fiber2 <- p.complete(io2).fork
    _ <- (fiber1.await *> fiber2.interrupt).fork
    _ <- (fiber2.await *> fiber1.interrupt).fork
    r <- p.await
  } yield r
```

---

### Exit

```scala
sealed trait Exit[+E, +A]
case class Success[A](v: A)           extends Exit[Nothing, A]
case class Failure[E](c: zio.Cause[E])extends Exit[E, Nothing]

sealed trait Cause[+E]
case class  Fail[E](value: E)     extends Cause[E]
case class  Die(value: Throwable) extends Cause[Nothing]
case object Interrupt             extends Cause[Nothing] 
```

---

### What we want to build

```scala
def unsafeRunAsync[E, A](
  io: => IO[E, A]
)(
  k: Exit[E, A] => Unit
): Unit

def unsafeRun[E, A](io: => IO[E, A]): Exit[E, A]
```

---

### Example Domain

Natural Numbers from Peano Axioms

```scala
sealed trait N
case object Zero extends N
case class Cons(n: N) extends N

@tailrec
def equals(n: N, m: N): Boolean =
  (n, m) match {
    case (Zero, Zero)         => true
    case (Cons(nn), Cons(mm)) => equals(nn, mm)
    case _                    => false
  }
```

---

### Naive Encoding of add, mul

```scala
// not tail recursive!
def add(n: N, m: N): N =
  m match {
    case Zero     => n
    case Cons(mm) => Cons(add(n, mm))
  }

// not tail recursive!
def mul(n: N, m: N): N =
  m match {
    case Zero     => Zero
    case Cons(mm) => add(mul(n, mm), n)
  }
```

---

### Stack Safe Encoding

```scala
type  UIO[A] = IO[Nothing, A] 
```

```scala
def add(n: N, m: N): UIO[N] =
  m match {
    case Zero     => UIO.succeed(n)
    case Cons(mm) => add(n, mm).map(Cons)
  }

def mul(n: N, m: N): UIO[N] =
  m match {
    case Zero     => UIO.succeed(Zero)
    case Cons(mm) => mul(n, mm).flatMap(o => add(o, n))
  }
```

---

### sub, div

```scala
sealed trait Err
case object Neg extends Err
case object Div0 extends Err

def sub(n: N, m: N): IO[Neg.type, N] =
  (n, m) match {
    case (_, Zero)            => IO.succeed(n)
    case (Zero, _)            => IO.fail(Neg)
    case (Cons(nn), Cons(mm)) => sub(nn, mm)
  }

def div(n: N, m: N): IO[Div0.type, N] 
```

---

### Parallel addAll

```scala
def addAll(ns: N*): UIO[N] =
  ns.toList match {
    case Nil      => UIO.succeed(Zero)
    case n :: Nil => UIO.succeed(n)
    case list =>
      val (l, r) = list.splitAt(ns.length / 2)
      for {
        lsf <- addAll(l: _*).fork
        rsf <- addAll(r: _*).fork
        ls <- lsf.join
        rs <- rsf.join
        s <- add(ls, rs)
      } yield s
  }
```

---

### Parallel Cooperative addAll

```scala
def addAll(ns: N*): UIO[N] =
  ns.toList match {
    case Nil      => UIO.succeed(Zero)
    case n :: Nil => UIO.succeed(n)
    case list =>
      val (l, r) = list.splitAt(ns.length / 2)
      for {
        lsf <- addAll(l: _*).fork
        rsf <- addAll(r: _*).fork
        ls <- lsf.join
        rs <- rsf.join
        _ <- UIO.yieldNow
        s <- add(ls, rs)
      } yield s
  }
```

---

# Let's go!

* ADT & combinators from ZIO
* Build our own Runtime and Fiber

---

 ### Take Representation of Effects from ZIO

 ```scala
// Moral equivalent of Kleisli[F[_], R, Either[E, A]]
trait ZIO[R, E, A]

// Produce an A or fails with E.
// Moral equivalent of F[Either[E, A]]
type  IO[E, A] = ZIO[Any, E, A] 

// Produce an A, never fail
// Moral equivalent of F[A]
type  UIO[A] = IO[Nothing, A] 
 ```

---

### Effect ADT


```scala
trait IO[E, A] {

  def flatMap[E1 >: E, B](k: A => IO[E1, B]): IO[E1, B] =
    new ZIO.FlatMap(self, k)

  def succeed[A](a: A): UIO[A] = new ZIO.Succeed(a)

  def fork: IO[R, Nothing, Fiber[E, A]] = new ZIO.Fork(self)

  ...

}
```

---

### Effect ADT

```scala
FlatMap          Succeed          Fail                   
Fold             EffectAsync      Fork          
Yield            EffectTotal      InterruptStatus        
CheckInterrupt   EffectPartial    DaemonStatus           
CheckDaemon      Descriptor       Lock                   
Access           Provide          EffectSuspendPartialWith 
FiberRefNew      FiberRefModify   Trace                  
TracingStatus    CheckTracing     EffectSuspendTotalWith 
RaceWith               
```

---

### Effect ADT

```scala
* FlatMap        * Succeed        * Fail                   
* Fold           * EffectAsync    * Fork          
* Yield          EffectTotal      InterruptStatus        
CheckInterrupt   EffectPartial    DaemonStatus           
CheckDaemon      Descriptor       Lock                   
Access           Provide          EffectSuspendPartialWith 
FiberRefNew      FiberRefModify   Trace                  
TracingStatus    CheckTracing     EffectSuspendTotalWith 
RaceWith               
```

---

### Approach

```scala
type Stack = List[Any => IO[Any, Any]]

@tailrec
private def step(v: Any, stack: Fiber.Stack): Exit
```

-----

```scala
class Succeed[A](val value: A) extends UIO[A]
class FlatMap[E, A](
    val io:      IO[E, A], 
    val k:  A => IO[E, A]
  ) extends IO[E, A] 
```

---

<section data-background="img/StackIllustration/StackIllustration.001.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.002.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.003.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.004.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.005.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.006.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.007.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.008.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.009.jpeg">

---

<section data-background="img/StackIllustration/StackIllustration.010.jpeg">

---

### Interpreting IOs

```scala
type Stack = List[Any => IO[Any, Any]]

type Interpreter =
  PartialFunction[    // may interpret just part of the ADT
    (
      IO[Any, Any],   // the next IO to interpret
      Any,            // the current input parameter
      Stack,          // the remainder of the stack
      Fiber[Any, Any] // the current fiber
    ),
    Interpretation
  ]

sealed trait Interpretation
case class  Step(v: Any, stack: Stack) extends Interpretation
case class  Return(exit: Exit[Any, Any]) extends Interpretation
case object Suspend extends Interpretation
```

---

### Fallback Interpreter 

```scala
val notImplemented: Interpreter = {
  case (io, v, stack, fiber) =>
    val e = 
      new IllegalStateException(
        s"not implemented: ${io.getClass}"
      )
    Return(Exit.die(e))
}
```

---

### Smallest Useful Interpreter: 

A Trampoline

```scala
class Succeed[A](val value: A) extends UIO[A]
class FlatMap[E, A](
    val io:      IO[E, A], 
    val k:  A => IO[E, A]
  ) extends IO[E, A] 
```

```scala
val succeedFlatMap: Interpreter = {
  case (s: Succeed, _, stack, _) => Step(s.value, stack)
  case (fm: FlatMap, v, stack, _) =>
    val newStack: Stack = stack
      .prepended(fm.k)
      .prepended(_ => fm.io)
    Step(v, newStack)
}
```

---

### Minimal Fiber

```scala
class Fiber[E, A](
    val interpreter: Fiber.Interpreter,
    val ec: ExecutionContext
) {   
  @volatile private var interrupted: Boolean = false
  @volatile private var result: Option[Exit[E, A]] = None
  @volatile private var listeners: List[Exit[E, A] => Unit] = Nil

  def register(callback: Exit[E, A] => Unit): Unit =
    listeners ::= callback

  def interrupt: UIO[Unit] = interrupted = true

  def schedule(v: Any, stack: Fiber.Stack): Unit =
    ec.execute(() => step(v, stack))

  @tailrec
  private def step(v: Any, stack: Fiber.Stack): Unit
 
 ...
}
```

---

```scala
  @tailrec
  private def step(v: Any, stack: Fiber.Stack): Unit = {
    val safeInterpretation: Interpretation =
      stack match {
        case Nil => Return(Exit.succeed(v.asInstanceOf[A]))
        case f :: tail =>
          Try(f(v)).fold(
            e  => Return(Exit.die(e)),
            io => interpreter.applyOrElse(
                    (io, v, tail, this), 
                    Fiber.notImplemented
                  )
          )
      }
    safeInterpretation match {
      case Suspend        =>
      case Step(v, stack) => step(v, stack)
      case Return(exit)   =>
        result.synchronized {
          result = Some(exit)
          listeners.foreach(_.apply(typedExit))
        }
    }
  }
}
```

---

### Runtime

```scala
class Runtime(interpreter: Interpreter)
             (implicit ec: ExecutionContext) {

  def unsafeRunAsync[E, A](
    io: => IO[E, A]
  )(
    k: Exit[E, A] => Unit
  ): Unit = {
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
```

---

### AddDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAdd`

---

### DivDemo

`bloop run runtime -m com.github.runtologist.demo.DemoDiv`

---

### fail

On error, fail right away, no recovery for now.

```scala
class Fail[E, A](val fill: (() => ZTrace) => Cause[E]) 
    extends IO[E, A]
```

```scala
case (f: Fail[_, _], _, _, fiber) =>
  // tracing not implemented
  val e: Cause[_] = 
    f.fill(() => ZTrace(fiberId = fiber.id, Nil, Nil, None))
  val exit: Exit[_, _] = Exit.halt(cause)
  Return(exit)
```

---

### DivDemo

`bloop run runtime -m com.github.runtologist.demo.DemoDiv`

---

### AddAllDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAddAll`

---

### fork and await

```scala
class Fork[E, A](val value: IO[E, A]) extends UIO[Fiber[E, A]]
class EffectAsync[E, A](
    val register: (IO[E, A] => Unit) => Option[IO[E, A]]
) extends IO[E, A]
```

```scala
case (f: ZIO.Fork[_, _, _], v, stack, parent) =>
  val fiber = new Fiber(parent.interpreter, parent.ec)
  fiber.schedule(v, List(_ => f.value))
  Step(fiber, stack)

case (ea: ZIO.EffectAsync[_, _, _], _, stack, fiber) =>
  val callback: IO[_, _] => Unit =
    io => fiber.schedule((), stack.prepended(_ => io))

  ea.register(callback) match {
    case None     => Suspend
    case Some(io) => Step((), stack.prepended(_ => io))
  }
```

---

### Fiber.await

```scala
class Fiber[E, A](...) {

  override def await: UIO[Exit[E, A]] =
    UIO.effectAsyncMaybe { k: (IO[E, A] => Unit) =>
      result.synchronized {
        result.fold[Option[UIO[Exit[E, A]]]] {
          register(exit => k(UIO.succeed(exit)))
          None
        }(r => Some(UIO.succeed(r)))
      }
    }

  ...
}
```

---

### Walkthrough join

---

### So far first come first serve

First step: Cooperative yielding

```scala
def addAll(ns: N*): UIO[N] =
  ns.toList match {
    case Nil      => UIO.succeed(Zero)
    case n :: Nil => UIO.succeed(n)
    case list =>
      val (l, r) = list.splitAt(ns.length / 2)
      for {
        lsf <- addAll(l: _*).fork
        rsf <- addAll(r: _*).fork
        ls <- lsf.join
        rs <- rsf.join

        _ <- ZIO.yieldNow
        
        s <- AddMul.add(ls, rs)
      } yield s
  }
````

---

### Yield

```scala
val doYield: Interpreter = {
  case (Yield, _, stack, fiber) =>
    fiber.schedule((), stack)
    Suspend
}
```

---

### CoopDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAllAddCoop`

---

### RR scheduling

```scala
class FairInterpreter(underlying: Interpreter, max: Int = 10) 
    extends Interpreter {

  var count = max

  override def apply(
      param: (IO[Any, Any], Any, Stack, Fiber[Any, Any])
  ): Interpretation = 
    underlying.apply(param) match {
      case Suspend       => Suspend
      case r @ Return(_) => r
      case Step(v, stack) if count < 1 =>
        count = max
        param._4.schedule(v, stack)
        Suspend
      case s @ Step(_, _) =>
        count -= 1
        s
    }
}
```

---

### FullDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAddAllFair`

---

### error handling

```scala
val program: IO[Nothing, String] =
  div(
    Cons(Cons(Zero)),
    Zero
  )
  .flatMap(r => UIO.succeed(r.toString))
  .catchAll(_ => UIO.succeed("Oh no, division by Zero!"))
```

---

#### Fold

```scala
class Fold[R, E, E2, A, B](
  val value: ZIO[R, E, A],
  val failure: Cause[E] => ZIO[R, E2, B],
  val success: A => ZIO[R, E2, B]
) extends ZIO[R, E2, B]
    with Function[A, ZIO[R, E2, B]] {

      def apply(v: A): ZIO[R, E2, B] = success(v)

    }
```

---

### Interpret Fold and Fail

```scala
case (fold: ZIO.Fold, v, stack, _) =>
  val newStack =
    stack
      .prepended(fold)
      .prepended((_: Any) => fold.value)
  Step(v, newStack)

case (fail: ZIO.Fail[_, _], _, stack, fiber) =>
  val cause = 
    fail.fill(() => ZTrace(fiberId = fiber.id, Nil, Nil, None))
  val tailWithFold =
    stack.dropWhile(f => !f.isInstanceOf[ZIO.Fold])
  tailWithFold match {
    case (handler: ZIO.Fold) :: tail =>
      val newStack = tail.prepended(handler.failure)
      Step(cause, newStack)
    case _ =>
      val exit = Exit.halt(cause)
      Return(exit)
  }
```

---

### ErrorHandlingDemo

`bloop run runtime -m com.github.runtologist.demo.DemoDivFold`

---

### More in ZIO
##### http://zio.dev

* Fiber Traces
* Fiber Locals
* Environmental Effects
* Interruptible/Uninterruptible regions
* EC pinning (really!)
* Fiber Dumps
* Manged Resources

---

# THX

###### https://github.com/runtologist/BuildingAnEffectRuntime

<img  src="img/icon__filled--light@3x.png" style="max-width:20%"> 

### We are hiring

##### https://risk42.com/
