package pir.node

import pir.lang._
import spatial.node.{Alloc, Control, Primitive}
import spatial.lang._

import argon._
import forge.tags._
import spatial.util.getCChains

import scala.collection.mutable

sealed abstract class ConfigBlackBox[T:Bits] extends Primitive[T]
@op case class GEMMConfig[T:Bits](a: T, b: T) extends ConfigBlackBox[T]
@op case class GEMVConfig[T:Bits](a: T, b: T) extends ConfigBlackBox[T]
@op case class CONVConfig[T:Bits](a: T, b: T) extends ConfigBlackBox[T]
@op case class StreamConfig1[T:Bits](a1: T) extends ConfigBlackBox[T]
@op case class StreamConfig2[T:Bits](a1: T, a2: T) extends ConfigBlackBox[T]
@op case class StreamConfig3[T:Bits](a1: T, a2: T, a3: T) extends ConfigBlackBox[T]
@op case class StreamConfig4[T:Bits](a1: T, a2: T, a3: T, a4: T) extends ConfigBlackBox[T]

@op case class CounterChainCopy(ctrs: Seq[Counter[_]]) extends Alloc[CounterChain]

abstract class PU extends Control[Void] {
  def ins: mutable.Map[Int,In[_]]
  def outs: mutable.Map[Int,Out[_]]

  def iterss: Seq[Seq[I32]]

  def ccs: Seq[CounterChain] = this.blocks.flatMap(getCChains)
  override def iters   = iterss.flatten
  override def cchains = ccs.zip(iterss)
  override def bodies  = Seq(iters -> this.blocks)
  def mayBeOuterBlock(i: Int): Boolean = true

  override def binds = super.binds ++ ins.values ++ outs.values ++ iters
}

// Virtual switch (outer controller)
@op case class VSwitch(
  scope:  Block[Void],
  iterss: Seq[Seq[I32]],
  ins:    mutable.Map[Int,In[_]] = mutable.Map.empty,
  outs:   mutable.Map[Int,Out[_]] = mutable.Map.empty
) extends PU {
  override def inputs = syms(scope)
}

// Address Generator
@op case class AG(
  datapath: Block[Void],
  iterss:   Seq[Seq[I32]],
  ins:      mutable.Map[Int,In[_]] = mutable.Map.empty,
  outs:     mutable.Map[Int,Out[_]] = mutable.Map.empty
) extends PU {
  override def inputs = syms(datapath)
}

// Virtual compute unit
@op case class VPCU(
  datapath: Block[Void],
  iterss:   Seq[Seq[I32]],
  ins:      mutable.Map[Int,In[_]] = mutable.Map.empty,
  outs:     mutable.Map[Int,Out[_]] = mutable.Map.empty
) extends PU {
  override def inputs  = syms(datapath)
}

// Virtual memory unit
@op case class VPMU(
  memories:    Seq[Sym[_]],
  var rdPath:  Option[Block[_]] = None,
  var rdIters: Seq[Seq[I32]] = Nil,
  var wrPath:  Option[Block[_]] = None,
  var wrIters: Seq[Seq[I32]] = Nil,
  ins:   mutable.Map[Int,In[_]] = mutable.Map.empty,
  outs:  mutable.Map[Int,Out[_]] = mutable.Map.empty
) extends PU {
  override def iterss = rdIters ++ wrIters
  override def cchains = rdPath.toSeq.flatMap{getCChains}.zip(rdIters) ++
                         wrPath.toSeq.flatMap{getCChains}.zip(wrIters)

  override def bodies = rdPath.map{blk => rdIters.flatten -> Seq(blk) }.toSeq ++
                        wrPath.map{blk => wrIters.flatten -> Seq(blk) }

  override def inputs = syms(rdPath) ++ syms(wrPath) ++ syms(memories)

  def getWdata(): Option[Sym[_]] = {
    wrPath.flatMap { b =>
      b.stms.find { case s @ Op(_:Data) => true; case _ => false }
    }
  }

  def setWr(addr: Block[_], iters: Seq[Seq[I32]]): Unit = {
    this.wrPath = Some(addr); this.wrIters = iters
  }
  def setRd(addr: Block[_], iters: Seq[Seq[I32]]): Unit = {
    this.rdPath = Some(addr); this.rdIters = iters
  }
}


