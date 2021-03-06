package spatial.lang

import forge.tags._
import argon._
import spatial.node.CounterChainNew
import pir.node.CounterChainCopy

/** Types */
@ref class CounterChain extends Top[CounterChain] with Ref[Array[Range],CounterChain] {
  override protected val __isPrimitive: Boolean = false
}
object CounterChain {
  @api def apply(ctrs: Seq[Counter[_]]): CounterChain = stage(CounterChainNew(ctrs))
  @rig def copy(ctrs: Counter[_]*): CounterChain = stage(CounterChainCopy(ctrs))
}


