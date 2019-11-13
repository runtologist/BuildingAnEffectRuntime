package zio

abstract class AbstractPMRFiber[E, A] extends Fiber[E, A] {

  @volatile private var result: Option[Exit[E, A]] = None
  private var listeners: List[ZIO.Succeed[Exit[E, A]] => Unit] = Nil

  def register(callback: Exit[E, A] => Unit): Unit =
    registerM(s => callback(s.value))
  protected def registerM(callback: ZIO.Succeed[Exit[E, A]] => Unit): Unit =
    listeners ::= callback

  protected def notify(exit: Exit[E, A]): Unit = result.synchronized {
    result = Some(exit)
    listeners.foreach(_(new ZIO.Succeed(exit)))
  }

  // implement Fiber trait

  override def await: UIO[Exit[E, A]] =
    result.synchronized {
      ZIO.effectAsyncMaybe[Any, Nothing, Exit[E, A]] { k =>
        result.fold[Option[UIO[Exit[E, A]]]] {
          listeners ::= k
          None
        }(r => Some(ZIO.succeed(r)))
      }
    }

  def poll: UIO[Option[Exit[E, A]]] = UIO.succeed(result)

  // not implemented
  override def interrupt: UIO[Exit[E, A]] = UIO.never
  override def inheritFiberRefs: UIO[Unit] = UIO.unit

}
