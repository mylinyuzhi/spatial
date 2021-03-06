package spatial.codegen.cppgen

import argon.codegen.cppgen.CppCodegen
import argon.core._

import spatial.nodes._

trait CppGenAlteraVideo extends CppCodegen {

  override protected def gen(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case AxiMSNew() => emit(src"""// axi_master_slave""")
    case _ => super.gen(lhs, rhs)
  }

}
