package spatial.node

import argon._
import forge.tags._
import spatial.lang._

@op case class SeriesForeach[A:Num](start: Num[A], end: Num[A], step: Num[A], func: Lambda1[A,Void])
       extends Op[Void] {
  val A: Num[A] = Num[A]
}
