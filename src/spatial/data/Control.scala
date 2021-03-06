package spatial.data

import argon._
import forge.tags._
import spatial.lang._
import spatial.util._
import utils.Tree

/** Control node schedule */
sealed abstract class Sched
object Sched {
  case object Seq extends Sched { override def toString = "Sequential" }
  case object Pipe extends Sched { override def toString = "Pipeline" }
  case object Stream extends Sched { override def toString = "Stream" }
  case object Fork extends Sched { override def toString = "Fork" }
  case object ForkJoin extends Sched { override def toString = "ForkJoin" }
}

/** The level of control within the hierarchy. */
sealed abstract class ControlLevel
case object InnerControl extends ControlLevel { override def toString = "InnerController" }
case object OuterControl extends ControlLevel { override def toString = "OuterController" }

sealed abstract class Ctrl {
  def s: Option[Sym[_]] = None
  def id: Int
  def parent: Ctrl
  def ancestors: Seq[Ctrl] = Tree.ancestors(this){_.parent}
  def ancestors(stop: Ctrl => Boolean): Seq[Ctrl] = Tree.ancestors(this, stop){_.parent}
  def ancestors(stop: Ctrl): Seq[Ctrl] = Tree.ancestors[Ctrl](this, {c => c == stop}){_.parent}
  @stateful def children: Seq[Controller]
}
case class Controller(sym: Sym[_], id: Int) extends Ctrl {
  override def s: Option[Sym[_]] = Some(sym)
  def parent: Ctrl = if (id != -1) Controller(sym,-1) else sym.parent
  @stateful def children: Seq[Controller] = sym.children
}
case object Host extends Ctrl {
  def id: Int = 0
  def parent: Ctrl = Host
  @stateful def children: Seq[Controller] = hwScopes.all
}

/** A controller's level in the control hierarchy. */
case class CtrlLevel(level: ControlLevel) extends StableData[CtrlLevel]
object levelOf {
  def get(x: Sym[_]): Option[ControlLevel] = metadata[CtrlLevel](x).map(_.level)
  def apply(x: Sym[_]): ControlLevel = levelOf.get(x).getOrElse{throw new Exception(s"Undefined control level for $x") }
  def update(x: Sym[_], level: ControlLevel): Unit = metadata.add(x, CtrlLevel(level))
}
object isOuter {
  def apply(x: Sym[_]): Boolean = levelOf(x) == OuterControl
  def update(x: Sym[_], isOut: Boolean): Unit = if (isOut) levelOf(x) = OuterControl else levelOf(x) = InnerControl
}

/** A controller's level in the control hierarchy. */
case class CtrParent(ctrParent: Sym[_]) extends StableData[CtrParent]
object ctrlNodeOf {
  def get(x: Sym[_]): Option[Sym[_]] = metadata[CtrParent](x).map(_.ctrParent)
  def apply(x: Sym[_]): Sym[_] = ctrlNodeOf.get(x).getOrElse{throw new Exception(s"Undefined counter parent for $x") }
  def update(x: Sym[_], ctrParent: Sym[_]): Unit = metadata.add(x, CtrParent(ctrParent))
}

/** The control schedule determined by the compiler. */
case class ControlScheduling(sched: Sched) extends StableData[ControlScheduling]
object styleOf {
  def get(x: Sym[_]): Option[Sched] = metadata[ControlScheduling](x).map(_.sched)
  def apply(x: Sym[_]): Sched = styleOf.get(x).getOrElse{throw new Exception(s"Undefined schedule for $x")}
  def update(x: Sym[_], sched: Sched): Unit = metadata.add(x, ControlScheduling(sched))
}

/** The control schedule annotated by the user, if any. */
case class SchedulingDirective(sched: Sched) extends StableData[SchedulingDirective]
object userStyleOf {
  def get(x: Sym[_]): Option[Sched] = metadata[SchedulingDirective](x).map(_.sched)
  def apply(x: Sym[_]): Sched = userStyleOf.get(x).getOrElse{throw new Exception(s"Undefined user schedule for $x") }
  def update(x: Sym[_], sched: Sched): Unit = metadata.add(x, SchedulingDirective(sched))
}

/** Unified method for extracting schedule. */
object scheduleOf {
  def get(x: Sym[_]): Option[Sched] = if (userStyleOf.get(x).isDefined) userStyleOf.get(x) else styleOf.get(x)
  def apply(x: Sym[_]): Sched = scheduleOf.get(x).getOrElse{throw new Exception(s"Undefined schedule for $x") }
  def update(x: Sym[_], sched: Sched): Unit = metadata.add(x, SchedulingDirective(sched))
}


/** Metadata holding a list of children within a controller. */
case class Children(children: Seq[Controller]) extends FlowData[Children]

/** Metadata holding the block of a controller within the controller hierarchy. */
case class ParentBlk(blk: Ctrl) extends FlowData[ParentBlk]

/** Metadata holding the parent of a controller within the controller hierarchy. */
case class ParentController(parent: Ctrl) extends FlowData[ParentController]

/** Metadata holding the counter associated with a loop iterator. */
case class IndexCounter(ctr: Counter[_]) extends FlowData[IndexCounter]
object ctrOf {
  def get[A](i: Num[A]): Option[Counter[A]] = {
    metadata[IndexCounter](i).map(_.ctr.asInstanceOf[Counter[A]])
  }
  def apply[A](i: Num[A]): Counter[A] = {
    ctrOf.get(i).getOrElse{throw new Exception(s"No counter associated with $i") }
  }
  def update(i: Num[_], ctr: Counter[_]): Unit = metadata.add(i, IndexCounter(ctr))
}


/** All accelerator scopes in the program */
case class AccelScopes(scopes: Seq[Controller]) extends FlowData[AccelScopes]
@data object hwScopes {
  def all: Seq[Controller] = globals[AccelScopes].map(_.scopes).getOrElse(Nil)
}
