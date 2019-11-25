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
---

## Build yourself an effect system

---

### Simon Schenk



<!-- <img  src="img/simon--rotated--small.jpg" style="float:right;transform:rotate(45deg);max-width:20%"> -->
<img  src="img/icon__filled--light@3x.png" style="max-width:20%"> 

CTO at Risk42 (https://risk42.com)

 ----

###### https://github.com/zio/interop-reactive-streams

###### https://github.com/zio/telemetry

 ----

simon@risk42.com - @runtologist

---

### Functional Effects

Immutable *value* that *models side-effects*

```scala
// does nothing, just describes writing to console
val effect: ZIO[Console, Nothing, Unit] = 
  console.putStrLn("What's your name?")
```

---

### vs Future

```scala
// does nothing, just describes writing to console
val effect: ZIO[Console, Nothing, Unit] = 
  console.putStrLn("What's your name?")

// immediately starts executing
val future: Future[Unit] = 
  Future(println("What's your name?"))
```

---

### Rich Combinators

```scala
// still does nothing
val readName = 
  console.putStrLn("What's your name?") *> console.readStrLn

// still nothing
val validName =
  readName.retry(Schedule.doUntil(_.length > 3))

// and still nothing
val program: ZIO[Console, Nothing, Unit] =
  validName.flatMap(name => console.putStrLn(s"Hello, $name!"))
```

---

### At the end of the world...

effects must be run in order to do anything 

```scala

val program =
  name.flatMap(name => console.putStrLn(s"Hello, $name!"))

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

### Errors

Expected Failure Modes

```scala
trait NameError
case object TooShort extends NameError

val readName: ZIO[Console, NameError, String] = 
  for {
    _ <- console.putStrLn("What's your name?") 
    name <- console.readStrLn
    _ <- IO.fail(TooShort).when(name.length < 3)
  } yield name

val r: Exit[NameError, String] = 
  runtime.unsafeRunSync(readName)
```

---

### vs Defects

<img src="img/bad-bug.svg" style="width:50%"/>

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
...
```

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

### Running Effects

* Execute in a *Fiber*
* Asynchronously
* Can fork more Fibers

```scala
val effect =
  for {
    _ <- console.putStrLn("What's your name?")
    fiber <- expensiveBackgroundWork.fork
    name <- console.readStrLn
    _ <- console.putStrLn(s"Hello, $name!")
    result <- fiber.join
  } yield result
runtime.unsafeRun(effect)
```

---

### Fiber

* Like thread:
  * Will run in parallel. 
  * (if multiple threads. Single threaded on ScalaJS.)
* Unlike Thread:
  * No mapping to OS level threads
  * No expensive context switched
  * Fine grained control

---

### Fiber

E.g. Erlang has Green Threads in Runtime

Java doesn't.

#### Fiber = Green thread implemented in Library

---

### Concurrency != Parallelism

> Concurrency is the composition of independently executing processes, while parallelism is the simultaneous execution of (possibly related) computations.
> Concurrency is about dealing with lots of things at once. Parallelism is about doing lots of things at once."

###### https://howtodoinjava.com/java/multi-threading/concurrency-vs-parallelism/

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

### Interpreter Running Effects in Fibers

 * stack safety
 * error model
 * parallelism fork/join
 * interruption
 * fair scheduling
 * error recovery

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
trait ZIO[R, E, A] {

  def flatMap[R1 <: R, E1 >: E, B](
      k: A => ZIO[R1, E1, B]
  ): ZIO[R1, E1, B] =
    new ZIO.FlatMap(self, k)

  def succeed[A](a: A): UIO[A] = new ZIO.Succeed(a)

  def fork: ZIO[R, Nothing, Fiber[E, A]] = new ZIO.Fork(self)

}
```

---

### Effect ADT

```scala
FlatMap           Succeed            EffectTotal              
Fail              Fold               InterruptStatus          
CheckInterrupt    EffectPartial      EffectAsync              
Fork              SuperviseStatus    EffectSuspendPartialWith 
Descriptor        FiberRefNew        Lock                     
FiberRefModify           
````

---

### Effect ADT

```scala
* FlatMap         * Succeed          EffectTotal              
* Fail            * Fold             InterruptStatus          
CheckInterrupt    EffectPartial      * EffectAsync              
* Fork            SuperviseStatus    EffectSuspendPartialWith 
Descriptor        FiberRefNew        Lock                     
FiberRefModify           
````

---

### Example Domain

Natural Numbers in Peano encoding

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
def add(n: N, m: N): N =
  m match {
    case Zero     => n
    case Cons(mm) => Cons(add(n, mm))
  }

def mul(n: N, m: N): N =
  m match {
    case Zero     => Zero
    case Cons(mm) => add(mul(n, mm), n)
  }
```

---

### Safe Encoding of add, mul

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
        _ <- ZIO.yieldNow
        ls <- lsf.join
        rs <- rsf.join
        s <- add(ls, rs)
      } yield s
  }
```

---

# Let's go!

---

### Interpreting IOs

```scala
type Stack = List[Any => IO[Any, Any]]

type Interpreter =
  PartialFunction[      // may interpret just part of the ADT
    (
        IO[Any, Any],   // the next IO to interpret
        Any,            // the current input parameter
        Stack,          // the remainder of the stack
        Fiber[Any, Any] // the current fiber
    ),
    Either[
      Option[Exit[Any, Any]], // suspend or exit
      (Any, Stack)            // continue with new state
    ]
  ]
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
    Left(Some(Exit.die(e)))
}
```

---

### Smallest Useful Interpreter: 

A Trampoline

```scala
class Succeed[A](val value: A) extends UIO[A]
class FlatMap[R, E, A](
    val zio:      ZIO[R, E, A], 
    val k:   A => ZIO[R, E, A]
  ) extends ZIO[R, E, A] 
```

```scala
val succeedFlatMap: Interpreter = {
  case (s: Succeed, _, stack, _) => Right((s.value, stack))
  case (fm: FlatMap, v, stack, _) =>
    val newStack: Stack = stack
      .prepended(fm.k)
      .prepended(_ => fm.zio)
    Right((v, newStack))
}
```

---

### Minimal Fiber

```scala
class Fiber[E, A](interpreter: Interpreter) {

  def run(io: IO[E, A]): Exit[E, A] = step((), List(() => io))

  @tailrec
  private def step(v: Any, stack: Fiber.Stack): Exit[E, A] = {
    val next: Either[Exit[E, A], Any] =
      for {
        f <- stack.headOption.toRight(left = Some(Exit.succeed(v)))
        io <- Try(f(v)).toEither.left.map(e => Some(Exit.die(e)))
        next <- interpreter.applyOrElse(
          (io, v, stack.tail, this),
          Fiber.notImplemented
        )
      } yield next
    next match {
      case Left(None) => Exit.die(new IllegalStateException())
      case Left(Some(exit)) => exit
      case Right((v, stack)) => step(v, stack)
    }
  }
}
```


---

### Fiber

notes: Code walkthrough

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

`bloop run runtime -m com.github.runtologist.demo.DemoAdd`

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
  val e = 
    f.fill(() => ZTrace(fiberId = fiber.id, Nil, Nil, None))
  val exit = Exit.halt(cause)
  Left(Some(exit))
```

---

### fork and await

```scala
class Fork[E, A](val value: IO[E, A]) extends UIO[Fiber[E, A]]
class EffectAsync[E, A](
    val register: (IO[E, A] => Unit) => Option[IO[E, A]]
) extends IO[E, A]
```

```scala
case (ea: EffectAsync, _, stack, fiber) =>
  def callback(vv: IO[_, _]): Unit =
    fiber.schedule((), stack.prepended(_ => vv))

  ea.register(callback) match {
    case None     => Left(None)
    case Some(io) => Right(((), stack.prepended(_ => io)))
  }

case (f: Fork, v, stack, parent) =>
  val fiber = new Fiber(parent.interpreter, parent.ec)
  fiber.schedule(v, List(_ => f.value))
  Right((fiber, stack))
```

also: Fiber.await

---

### So far first come first serve

First step: Cooperative yielding

```scala
val doYield: Interpreter = {
  case (Yield, _, stack, fiber) =>
    fiber.schedule((), stack)
    Left(None)
}
```


---

### CoopDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAllAddCoop`

---

### RR scheduling

```scala
class FairInterpreter(underlying: Interpreter) 
    extends Interpreter {
  val yieldAfter = 10
  var count = yieldAfter

  def apply(param: InterpreterParams): Interpretation = {
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

  // implement PartialFunction 
  override def isDefinedAt ...
}
```

---

### FullDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAllAdd`

---

### error handling

#### Fold

```scala
class Fold[R, E, E2, A, B](
  val value: ZIO[R, E, A],
  val failure: Cause[E] => ZIO[R, E2, B],
  val success: A => ZIO[R, E2, B]
) extends ZIO[R, E2, B]
    with Function[A, ZIO[R, E2, B]] 
```

---

## error handling

```scala
case (fold: ZIO.Fold, v, stack, _) =>
  val newStack =
    stack
      .prepended(fold)
      .prepended((_: Any) => fold.value)
  Right((v, newStack))

case (fail: ZIO.Fail[_, _], _, stack, fiber) =>
  val cause = 
    fail.fill(() => ZTrace(fiberId = fiber.id, Nil, Nil, None))
  val tailWithFold =
    stack.dropWhile(f => !f.isInstanceOf[ZIO.Fold])
  tailWithFold match {
    case (handler: ZIO.Fold) :: tail =>
      val newStack = tail.prepended(handler.failure)
      Right((cause, newStack))
    case _ =>
      val exit = Exit.halt(cause)
      Left(Some(exit))
  }
```

---

# THX
