package spatial.node

import argon._
import forge.tags._

import spatial.lang._

@op case class IfThenElse[T:Type](cond: Bit, thenBlk: Block[T], elseBlk: Block[T]) extends Control[T] {

  override def aliases: Seq[Sym[_]] = syms(thenBlk.result, elseBlk.result)

  override def iters = Nil
  override def cchains = Nil
  override def bodies = Seq(Nil -> Seq(thenBlk,elseBlk))
  def mayBeOuterBlock(i: Int): Boolean = true
}
