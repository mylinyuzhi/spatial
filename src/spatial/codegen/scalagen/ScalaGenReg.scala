package spatial.codegen.scalagen

import argon._
import spatial.lang._
import spatial.node._

trait ScalaGenReg extends ScalaCodegen with ScalaGenMemories {

  override protected def remap(tp: Type[_]): String = tp match {
    case tp: Reg[_] => src"Array[${tp.A}]"
    case _ => super.remap(tp)
  }

  override protected def gen(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case op@RegNew(init)    => emitMemObject(lhs){ emit(src"object $lhs extends Ptr[${op.A}]($init)") }
    case RegReset(reg, ens) =>
      val init = reg match {case Op(RegNew(i)) => i }
      emit(src"val $lhs = if (${and(ens)}) $reg.set($init)")

    case RegRead(reg)       => emit(src"val $lhs = $reg.value")
    case RegWrite(reg,v,en) => emit(src"val $lhs = if (${and(en)}) $reg.set($v)")

    case op@ArgInNew(init) => emitMemObject(lhs){ emit(src"object $lhs extends Ptr[${op.A}]($init)") }
    case SetReg(reg, v)  => emit(src"val $lhs = $reg.set($v)")

    case op@ArgOutNew(init)    => emitMemObject(lhs){ emit(src"object $lhs extends Ptr[${op.A}]($init)") }
    case GetReg(reg)        => emit(src"val $lhs = $reg.value")


    //case RegWriteAccum(reg,data,first,en,_) =>
    //  emit(src"val $lhs = if ($en && $first) $reg.update(0,$data) else if ($en) $reg.update(0,$data + $reg.apply(0))")
    case _ => super.gen(lhs, rhs)
  }

}
