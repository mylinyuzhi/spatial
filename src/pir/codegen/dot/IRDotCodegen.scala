package pir.codegen.dot

import argon._
import argon.codegen.Codegen
import pir.lang._
import pir.node._
import spatial.lang._

import scala.language.implicitConversions

case class IRDotCodegen(IR: State) extends Codegen with DotCommon {
  override def entryFile: String = s"IRGraph.$ext"
  override def ext = s"dot.$lang"

  def emitEntry(block: Block[_]): Unit = {}

  override protected def quoteOrRemap(arg: Any): String = arg match {
    case s: SRAM[_,_] => s"$s"
    case s: Void => s"$s"
    case s: Lanes => s"$s"
    case s: Out[_] => s"$s"
    case s: In[_] => s"$s"
    case _ => super.quoteOrRemap(arg)
  }

  // Set the Dot attributes
  val attributes = DotAttr()
  attributes.shape(box)
            .style(filled)
            .color(black)
            .fill(white)
            .labelfontcolor(black)

  override protected def visitBlock[R](block: Block[R]): Block[R] = {
    val subgraphAttr = DotAttr().style(filled)
              .color(blue)
              .fill(white)
              .label(s"Block_${getNodeName(block.result)}")
              .labelfontcolor(black)

    emitSubgraph(subgraphAttr) {
      super.visitBlock(block)
    }
    block
  }

  private def needsSubgraph(rhs: Op[_]): Boolean = rhs match {
    case pcu: VPCU => true
    case pmu: VPMU => true
    case _ => false
  }

  private def getSubgraphAttr(lhs: Sym[_], rhs: Op[_]): DotAttr = {
    val subgraphAttr = DotAttr()
    val color = getNodeColor(rhs)

    // Default attributes
    subgraphAttr.style(filled)
              .color(black)
              .fill(color)
              .label(getNodeName(lhs))
              .labelfontcolor(black)

  }

  private def getNodeAttr(lhs: Sym[_]): DotAttr = {
    val nodeAttr = DotAttr()
    val color = lhs.op match {
      case Some(x) => white
      case None => lightgrey
    }

    nodeAttr.style(filled)
              .shape(box)
              .color(black)
              .fill(color)
              .labelfontcolor(black)
  }

  private def visitCommon(lhs: Sym[_], rhs: Op[_]): Unit = {
    gen(getNodeName(lhs), getNodeAttr(lhs))
    rhs.binds.foreach { b =>
      gen(getNodeName(b), getNodeAttr(b))
      emitEdge(getNodeName(b), getNodeName(lhs))
    }
    rhs.inputs.foreach { in =>
      if (in.isBound) gen(getNodeName(in), getNodeAttr(in))
      emitEdge(getNodeName(in), getNodeName(lhs))
    }

    rhs match {
      case pcu: VPCU =>
        visitBlock(pcu.datapath)
      case pmu: VPMU =>
        pmu.rdPath.map { b => visitBlock(b) }
        pmu.wrPath.map { b => visitBlock(b) }
      case _ =>
    }
  }

  override protected def gen(lhs: Sym[_], rhs: Op[_]): Unit = {
    dbgs(s"[IRDotCodegen] visit $lhs, $rhs, binds: ${rhs.binds}")

    if (needsSubgraph(rhs)) {
      emitSubgraph(getSubgraphAttr(lhs, rhs)) { visitCommon(lhs, rhs) }
    }
    else {
      visitCommon(lhs, rhs)
    }
  }
}


