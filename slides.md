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

## Simon Schenk

<img  src="img/simon--rotated--small.jpg" style="float:right;transform:rotate(45deg);max-width:20%">

CTO at Risk42 (https://risk42.com)

simon@risk42.com - @runtologist

 * Query Engine in Java 6 
 * Scala 2.7 as better Java 
 * Akka Cluster 
 * Akka Streams 
 * `Kleisli[IO[_], R, Either[E, A]]` 
 * `ZIO[R, E, A]`

---

## Functional Effects

Immutable *value* that *models side-effects*

```scala
// does nothing, just describes writing to console
val effect: ZIO[Console, Nothing, Unit] = 
  Console.putStrLn("What's your name?")
```

---

## vs Future

```scala
// does nothing, just describes writing to console
val effect: ZIO[Console, Nothing, Unit] = 
  Console.putStrLn("What's your name?")

// immediately starts executing
val future: Future[Unit] = 
  Future(println("What's your name?"))
```

---

## Rich combinators

```scala
// still does nothing
val readName = 
  Console.putStrLn("What's your name?") *> Console.readStrLn

// still nothing
val validName =
  readName.retry(Schedule.doUntil(_.length > 3))

// and still nothing
val program: ZIO[Console, Nothing, Unit] =
  validName.flatMap(name => Console.putStrLn(s"Hello, $name!"))
```

---

## At the end of the world...

effects must be run in order to do anything 

```scala

val program =
  name.flatMap(name => Console.putStrLn(s"Hello, $name!"))

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

## errors

Expected Failure Modes

```scala
trait NameError
case object TooShort extends NameError

val readName: ZIO[Console, NameError, String] = 
  for {
    _ <- Console.putStrLn("What's your name?") 
    name <- Console.readStrLn
    _ <- IO.fail(TooShort).when(name.length < 3)
  } yield name

val r: Exit[NameError, String] = 
  runtime.unsafeRunSync(readName)
```

---

## vs Defects

<img src="img/bad-bug.svg" style="width:50%"/>

---

## Exit

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

### interruption

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

## running effects

* Execute in a *Fiber*
* Asynchronously
* Can fork more Fibers

```scala
val effect =
  for {
    _ <- Console.putStrLn("What's your name?")
    name <- Console.readStrLn
    _ <- Console.putStrLn(s"Hello, $name!")
  } yield ()
runtime.unsafeRun(effect)
```

---

## Fiber

Think of Fibers like threads by all means. Will run in parallel. Obviously only parallel if multiple threads. e.g. ZIO works on ScalaJS single threaded.
Everything runs in a fiber. Like Threads. In doubt, main thread

Erlang: Green Threads in Runtime
Java: not
Fiber = Green thread implemented in Library

Concurrency != parallelism

"Concurrency is the composition of independently executing processes, while parallelism is the simultaneous execution of (possibly related) computations.
Concurrency is about dealing with lots of things at once. Parallelism is about doing lots of things at once."
https://howtodoinjava.com/java/multi-threading/concurrency-vs-parallelism/

Fiber == Lightweight thread, all executing in parallel.
Concurrently on a concurrent platform. e.g. ZIO fibers single threaded on ScalaJS, but still parallel

Thread:
	def start => needs to start explicitly
	def interrupt => can be interrupted
		interruption flag checked eventually
	def run => no parameters, no return value

Fiber[E, A]	
	can return error and result
	
Start Fiber by
	myZio.fork
Forking again is an effect, only executed on run.


example: for { _ <- sleep *> prinlnt; _<-readln } ()
Example: join


# lazy (vs future)

---

## What we want to build

```scala
def unsafeRunAsync[E, A](
  io: => IO[E, A]
)(
  k: Exit[E, A] => Unit
): Unit

def unsafeRun[E, A](io: => IO[E, A]): Exit[E, A]
```

---

## interpreter running effects in fibers

 * stack safety
 * error model
 * parallelism fork/join
 * interruption
 * fair scheduling

---
 
 ### Take representation of effects from ZIO

 ```scala
// Moral equivalent of Reader[R, Either[E, A]]
// or rather Kleisli[F[_], R, Either[E, A]]
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
def flatMap[R1 <: R, E1 >: E, B](k: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
    new ZIO.FlatMap(self, k)
def succeed[A](a: A): UIO[A] = new ZIO.Succeed(a)
def fork: ZIO[R, Nothing, Fiber[E, A]] = new ZIO.Fork(self)
```

---

### Effect ADT #2

```scala
FlatMap           Succeed            EffectTotal              
Fail              Fold               InterruptStatus          
CheckInterrupt    EffectPartial      EffectAsync              
Fork              SuperviseStatus    EffectSuspendPartialWith 
Descriptor        FiberRefNew        Lock                     
FiberRefModify           
````

---

### Effect ADT #2

```scala
* FlatMap         * Succeed          EffectTotal              
* Fail            Fold               InterruptStatus          
CheckInterrupt    EffectPartial      * EffectAsync              
* Fork            SuperviseStatus    EffectSuspendPartialWith 
Descriptor        FiberRefNew        Lock                     
FiberRefModify           
````

---

# example domain

Natural numbers in Peano encoding

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

## naive encoding of add, mul

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

## safe encoding of add, mul

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

## sub, div

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

## parallel addAll

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
        s <- AddMul.add(ls, rs)
      } yield s
  }
```

---

## parallel cooperative addAll

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
        s <- AddMul.add(ls, rs)
      } yield s
  }
```

---

## Interpreting IOs

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

## fallback interpreter 

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

### smallest useful interpreter: 

A Trampoline

```scala
class Succeed[A](val value: A) extends UIO[A]
class FlatMap[R, E, A0, A](
    val zio: ZIO[R, E, A0], 
    val k: A0 => ZIO[R, E, A]
  ) extends ZIO[R, E, A] 


val succeedFlatMap: Interpreter = {
  case (s: Succeed[_], _, stack, _) => 
    Right((s.value, stack))
  case (fm: FlatMap[_, _, _, _], v, stack, _) =>
    val newStack: Stack = stack
      .prepended(fm.k.asInstanceOf[Any => IO[Any, Any]])
      .prepended(_ => fm.zio)
    Right((v, newStack))
}
```

---

### minimal fiber

```scala
class Fiber[E, A](interpreter: Interpreter) {

  def run(io: IO[E, A]): Exit[E, A] = step((), List(() => io))

  @tailrec
  private def step(v: Any, stack: Fiber.Stack): Exit[E, A] = {
    val next =
      for {
        f <- stack.headOption.toRight(Some(Exit.succeed(v)))
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

## Fiber & Runtime

notes: Code walkthrough

---

## AddDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAdd`

---

## DivDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAdd`

---

## fail

On error, fail right away, no recovery for now.

```scala
val fail: Interpreter = {
  case (f: Fail[_, _], _, _, _) =>
    // tracing not implemented
    val e = f.fill(() => ZTrace(fiberId = 0L, Nil, Nil, None))
    val exit =
      e.failureOption.map(Exit.fail).orElse(e.dieOption.map(Exit.die)).get
    Left(Some(exit))
}
```

---

## fork and await

```scala
val forkEffectAsync: Interpreter = {
  case (ea: EffectAsync[_, _, _], _, stack, fiber) =>
    def callback(vv: ZIO[Any, Any, Any]): Unit =
      fiber.schedule((), stack.prepended(_ => vv))

    ea.register(callback) match {
      case None     => Left(None)
      case Some(io) => Right(((), stack.prepended(_ => io)))
    }
  case (f: Fork[_, _, _], v, stack, parent) =>
    val fiber = new Fiber(parent.interpreter, parent.ec)
    fiber.schedule(v, List(_ => f.value))
    Right((fiber, stack))
}
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

## CoopDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAllAddCoop`

---

## RR scheduling

```scala
class FairInterpreter(underlying: Interpreter) 
    extends Interpreter {
  val yieldAfter = 10
  var count = yieldAfter

  override def apply(param: InterpreterParams): Interpretation = {
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

## FullDemo

`bloop run runtime -m com.github.runtologist.demo.DemoAllAdd`

---

# Backup

notes: 

Functional Effects

A "functional XYZ" is an immutable value that models XYZ. Typically functional things have (math-like) functions that, given an old XYZ, return a new XYZ, which represents the old model with the specified operation applied.
For example, a functional effect is an immutable value that models side-effects, like printing text to a console or reading bytes from a socket or mutating a piece of memory. Functional effects, as immutable values, don't actually do anything, they just create an in-memory model of things to be done. Helper functions like map, flatMap, and many others, help you build complex models out of simple pieces in a very flexible way.
Functional effects have to be "run", which means the model has to be translated into operations. For some types of effects (state, reader, writer, option, either), this can be done in a purely functional way, but for IO / Task / F[_] like effects, this cannot be done in a purely functional way, which means it's best to "run" your whole program at the top-level main function, which is what Haskell does.
To show another example, a "functional mutable field" is a model of a mutable field, which consists of an immutable pair of (path) functions: a getter and a setter, which operate on immutable values. This is otherwise known as a "lens".
All functional things are values, and you do things with them using (math) functions. The fact that they are all values in functional programming lets you use all the value-level machinery (passing to functions, returning from functions, storing in data structures, and building functions to extract out duplication from expressions). This is what makes functional programming so uniform and so incredibly concise / powerful / free of duplication.
In functional programming, everything is "first-class".
https://www.reddit.com/r/hascalator/comments/ald8qs/what_is_functional_effect/

here: Side effects. 
e.g. Option encapsulates the effect of optionality
IO encapsulates side effects
=> represent the concept of a side effect without executing it.
Pros:
  * testable
  * composable. Example: 



Fibers talk

Erlang: Green Threads in Runtime
Java: not
Fiber = Green thread implemented in Library
Think of Fibers like threads by all means. Will run in parallel. Obviously only parallel if multiple threads. e.g. ZIO works on ScalaJS single threaded.
Concurrency != parallelism
Fiber == Lightweight thread, all executing in parallel.

Thread:
	def start => needs to start explicitly
	def interrupt => can be interrupted
		interruption flag checked eventually
	def run => no parameters, no return value

Fiber[E, A]	
	can return error and result
	
Start Fiber by
	myZio.fork

Everything runs in a fiber. Like Threads. In doubt, main thread


example: for { _ <- sleep *> prinlnt; _<-readln } ()
Example: join

