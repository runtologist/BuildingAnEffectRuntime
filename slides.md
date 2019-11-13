---
title: Build yourself an effect system
theme: black
highlightTheme: vs2015
revealOptions:
    transition: 'fade'
---

# Build yourself an effect system

---

## Simon Schenk

<img  src="simon--rotated--small.jpg" style="float:right;transform:rotate(45deg);max-width:20%">

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
  readName.retry(Schedule.doUntil(_.length > 2))
// and still nothing
val program: ZIO[Console, Nothing, Unit] =
  name.flatMap(name => Console.putStrLn(s"Hello, $name!"))
```

---

## At the end of the world...

effects must be run in order to do anything 

```scala
val readName = 
  Console.putStrLn("What's your name?") *> Console.readStrLn
val validName =
  readName.retry(Schedule.doUntil(_.length > 3))
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

### interruption

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

### Take representation of effects from ZIO

 ```scala
 UIO[A] // Produces an A, cannot fail
 UIO[Nothing] // Never terminates
 IO[E, A] // Produces an A or fails with an E.
 def IO.fail(e): IO[E, Nothing] // Never produces anything
 Task[A] =:= IO[Throwable, A]
 ```

notes:

Show implementation of Succeed / Fail / FlatMap

---

# build interpreter running effects in fibers

 * stack safety
 * error model
 * parallelism fork/join
 * interruption
 * fair scheduling

 Show API of Runtime
 
---

# example domain

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

