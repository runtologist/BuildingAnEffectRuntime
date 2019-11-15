package com.github.runtologist.naturalNumbers

import com.github.runtologist.naturalNumbers.N._

import zio.UIO
import zio.ZIO

object AddAllCoop {

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

}
