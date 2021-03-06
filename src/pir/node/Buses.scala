package pir.node

import pir.lang._
import spatial.node.{Alloc, Primitive}
import spatial.lang._

import argon._
import forge.tags._

sealed abstract class ICBus[A:Bits] extends Alloc[A]
object ICBus {
  def unapply(x: Sym[_]): Option[Sym[_]] = x.op match {
    case Some(_:ICBus[_]) => Some(x)
    case _ => None
  }
}

@op case class ScalarBus[A:Bits](out: Out[A], in: In[A]) extends ICBus[Word]
@op case class VectorBus[A:Bits](out: Out[A], in: In[A]) extends ICBus[Lanes]
@op case class ControlBus(out: Out[Bit], in: In[Bit]) extends ICBus[Bit]

@op case class ReadIn[A:Bits](bus: In[A]) extends Primitive[A]
@op case class WriteOut[A:Bits,B:Bits](bus: Out[A], b: Bits[B]) extends Primitive[Void]

@op case class Addr(addr: I32) extends Primitive[Void]
@op case class Data(data: Bits[_]) extends Primitive[Void]
