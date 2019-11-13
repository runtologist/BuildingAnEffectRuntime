package com.github.runtologist.pmr

// A syncronous equivaluent of Promise
class OneShot[A] {

  @volatile var v: Option[A] = None

  def set(vv: A): Unit = synchronized {
    v.fold {
      v = Some(vv)
      this.notifyAll()
    }(_ => ())
  }

  def get: A = {
    while (v.isEmpty) {
      synchronized {
        if (v.isEmpty) this.wait()
      }
    }
    v.get
  }
}
