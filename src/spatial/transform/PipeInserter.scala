package spatial.transform

import argon._
import argon.transform.MutateTransformer

import spatial.data._
import spatial.util._
import spatial.lang._
import spatial.node._
import spatial.internal._

import scala.collection.mutable.ArrayBuffer

case class PipeInserter(IR: State) extends MutateTransformer {
  var enable: Set[Bit] = Set.empty

  def withEnable[T](en: Bit)(blk: => T)(implicit ctx: SrcCtx): T = {
    val saveEnable = enable
    enable = enable + en
    val result = blk
    enable = saveEnable
    result
  }


  private class Stage(val inner: Boolean) {
    val nodes: ArrayBuffer[Sym[_]] = ArrayBuffer[Sym[_]]()

    def outer: Boolean = !inner

    def dump(i: Int): Unit = {
      dbgs(s"Stage #$i: " + (if (inner) "[Inner]" else "[Outer]"))
      nodes.foreach { s => dbgs(s"  ${stm(s)}") }
    }

    lazy val inputs: Set[Sym[_]] = nodes.toSet.flatMap { s: Sym[_] => s.nestedInputs }
  }

  private object Stage {
    def outer = new Stage(inner = false)
    def inner = new Stage(inner = true)
  }

  override def transform[A: Type](lhs: Sym[A], rhs: Op[A])(implicit ctx: SrcCtx): Sym[A] = rhs match {
    case switch @ Switch(F(selects), _) if isOuterControl(lhs) =>
      val res: Option[Either[Reg[A],Var[A]]] = if (Type[A].isVoid) None else Some(resFrom(lhs))

      val cases = switch.cases.zip(selects).map { case (SwitchCase(body), sel) =>
        val controllers = lhs.children
        val primitives = body.stms.collect{case Primitive(s) => s }
        val requiresWrap = controllers.length > 1 || (primitives.nonEmpty && controllers.nonEmpty)

        () => withEnable(sel) {
          val body2: Block[Void] = {
            if (requiresWrap) wrapSwitchCase(body, res)
            else stageScope(f(body.inputs),body.options){ insertPipes(body, res, scoped = false).right.get }
          }
          op_case(body2)
        }
      }
      val switch2: Void = op_switch(selects, cases)
      transferMetadata(lhs -> switch2)
      res match {
        case Some(r) => resRead(r)                    // non-void
        case None    => switch2.asInstanceOf[Sym[A]]  // Void case
      }

    case _ =>
      if (isOuterControl(lhs)) {
        dbgs(s"$lhs = $rhs")
        rhs.blocks.zipWithIndex.foreach { case (block, id) =>
          dbgs(s"  block #$id [" + (if (isOuterBlock(Controller(lhs,id))) "Outer]" else "Inner]"))
          if (isOuterBlock(Controller(lhs, id))) register(block -> insertPipes(block).left.get)
        }
      }
      super.transform(lhs, rhs)
  }

  def wrapSwitchCase[A:Type](body: Block[A], res: Option[Either[Reg[A],Var[A]]])(implicit ctx: SrcCtx): Block[Void] = {
    stageScope(f(body.inputs), body.options){
      Pipe(enable, {
        insertPipes(body, res, scoped = false).right.get
      })
    }
  }

  protected def insertPipes[R](block: Block[R], res: Option[Either[Reg[R],Var[R]]] = None, scoped: Boolean = true): Either[Block[R],Sym[Void]] = {
    val blk = stageScopeIf(scoped, f(block.inputs), block.options){
      val stages = ArrayBuffer[Stage]()
      def curStage: Stage = stages.last
      def nextInnerStage: Stage = {
        if (curStage.outer) { stages += Stage.inner }
        curStage
      }
      def nextOuterStage: Stage = {
        if (curStage.inner) { stages += Stage.outer }
        curStage
      }
      stages += Stage.outer

      block.stms.foreach{
        case Transient(s) =>
          val i = stages.lastIndexWhere{stage =>
            (stage.nodes intersect s.inputs).nonEmpty
          }
          val stage = if (i >= 0) stages(i) else stages.head
          stage.nodes += s

        case Alloc(s)      => nextOuterStage.nodes += s
        case Primitive(s)  => nextInnerStage.nodes += s
        case Control(s)    => nextOuterStage.nodes += s
        case FringeNode(s) => nextOuterStage.nodes += s
      }

      stages.zipWithIndex.foreach{
        case (stg,i) if stg.inner =>
          stg.dump(i)
          val calculated = stg.nodes.toSet
          val escaping = stg.nodes.filter{s =>
            !s.tp.isVoid && (s == block.result || (s.consumers diff calculated).nonEmpty)
          }
          dbgs(s"Escaping: ")
          escaping.foreach{s => s"  ${stm(s)}"}
          val ress = escaping.map{
            case s if s == block.result && res.isDefined => res.get
            case s => resFrom(s)
          }

          implicit val ctx: SrcCtx = SrcCtx.empty
          val pipe = Pipe {
            isolateSubst{
              stg.nodes.foreach(visit)
              escaping.zip(ress).foreach{case (s, r) => resWrite(r,s) }
            }
          }
          isOuter(pipe) = false
          escaping.zip(res).foreach{case (s,r) => register(s -> resRead(r)) }

        case (stg,i) if stg.outer =>
          stg.dump(i)
          stg.nodes.foreach(visit)
      }

      (block.result match {
        case _:Void => void
        case s      => f(s)
      }).asInstanceOf[Sym[R]]
    }

    blk match {
      case Left(b)  => Left(b)
      case Right(_) => Right(void)
    }
  }

  def resFrom[A](s: Sym[A]): Either[Reg[A],Var[A]] = s match {
    case b: Bits[_] => Left(regFrom(b.asInstanceOf[Bits[A]]))
    case _          => Right(varFrom(s))
  }
  def resWrite[A](x: Either[Reg[_],Var[_]], d: Sym[A]): Void = x match {
    case Left(reg)  => regWrite(reg.asInstanceOf[Reg[A]], d.asInstanceOf[Bits[A]])
    case Right(vrr) => varWrite(vrr.asInstanceOf[Var[A]], d)
  }
  def resRead[A](x: Either[Reg[A],Var[A]]): Sym[A] = x match {
    case Left(reg)  => regRead(reg)
    case Right(vrr) => varRead(vrr)
  }


  def regFrom[A](s: Bits[A]): Reg[A] = {
    implicit val ctx: SrcCtx = s.ctx
    implicit val tA: Bits[A] = s.tp.view[Bits]
    Reg.alloc[A](s.zero)
  }
  def regRead[A](x: Reg[A]): Sym[A] = {
    implicit val ctx: SrcCtx = x.ctx
    implicit val tA: Bits[A] = x.A
    tA.box(Reg.read(x))
  }
  def regWrite[A](x: Reg[A], data: Bits[A]): Unit = {
    implicit val ctx: SrcCtx = x.ctx
    Reg.write(x,data)
  }

  def varFrom[A](s: Type[A])(implicit ctx: SrcCtx): Var[A] = {
    implicit val tA: Type[A] = s
    Var.alloc[A](None)
  }
  def varFrom[A](s: Sym[A]): Var[A] = {
    implicit val ctx: SrcCtx = s.ctx
    varFrom(s.tp)
  }
  def varRead[A](x: Var[A]): Sym[A] = {
    implicit val ctx: SrcCtx = x.ctx
    implicit val tA: Type[A] = x.A
    Var.read(x)
  }
  def varWrite[A](x: Var[A], data: Sym[A]): Unit = {
    implicit val tA: Type[A] = x.A
    Var.assign(x,data.unbox)
  }

}
