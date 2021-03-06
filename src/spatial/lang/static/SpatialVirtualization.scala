package spatial.lang
package static

import argon._
import forge.tags._
import forge.{VarLike,Ptr}

import utils.Overloads._
import spatial.node.IfThenElse

import language.experimental.macros
import scala.reflect.macros.whitebox

trait SpatialVirtualization {
  import SpatialVirtualization._

  // Generally unused/unsupported methods
  implicit class VirtualizeAnyRefMethods(lhs: AnyRef) {
    def infix_eq(rhs: AnyRef): Boolean = lhs eq rhs
    def infix_ne(rhs: AnyRef): Boolean = lhs ne rhs
    def infix_notify(): Unit = lhs.notify()
    def infix_notifyAll(): Unit = lhs.notifyAll()
    def infix_synchronized[T](body: T): T = lhs.synchronized(body)
    def infix_wait(): Unit = lhs.wait()
    def infix_wait(timeout: Long): Unit = lhs.wait(timeout)
    def infix_wait(timeout: Long, nanos: Int): Unit = lhs.wait(timeout, nanos)
    def infix_clone(): AnyRef = lhs.clone()
    def infix_finalize(): Unit = lhs.finalize()
  }

  def __valName(init: Any, name: String): Unit = init match {
    case s: Sym[_] => s.name = Some(name)
    case _ => ()
  }

  @rig def __newVar[T](init: T): VarLike[T] = init match {
    case t: Top[_] =>
      implicit val T: Type[T] = t.selfType.asInstanceOf[Type[T]]
      Var.alloc(Some(init))

    case _ => new Ptr[T](init)
  }

  @rig def __assign[T](v: VarLike[T], rhs: Any): Unit = v match {
    case v: Var[a] => v.__assign(v.A.from(rhs).asInstanceOf[T])
    case _ =>
      try { v.__assign(rhs.asInstanceOf[T]) }
      catch {case _:Throwable =>
        error(ctx, s"Type mismatch: Cannot assign ${rhs.getClass.getSimpleName} to var of type ${v.__read.getClass.getSimpleName}")
        error(ctx)
      }
  }

  def __use[T](v: T): T = macro forge.EmbeddedControls.useImpl[T]
  @rig def __use[T](v: Ptr[T]): T = v.__read
  @rig def __use[T](v: Var[T]): T = v.__sread()
  @rig def __use[T](v: Reg[T]): T = v.value

  @rig def __ifThenElse[A:Type](cond: Bit, thenBr: => Lift[A], elseBr: => Lift[A])(implicit ov0: Overload0): A = {
    ifThenElse(cond, () => thenBr.unbox, () => elseBr.unbox)
  }
  @rig def __ifThenElse[A:Type](cond: Bit, thenBr: => Sym[A], elseBr: => Literal)(implicit ov1: Overload1): A = {
    ifThenElse(cond, () => thenBr, () => Type[A].from(elseBr.value))
  }
  @rig def __ifThenElse[A:Type](cond: Bit, thenBr: => Literal, elseBr: => Sym[A])(implicit ov2: Overload2): A = {
    ifThenElse(cond, () => Type[A].from(thenBr.value), () => elseBr)
  }
  @rig def __ifThenElse[A](cond: Bit, thenBr: => Sym[A], elseBr: => Sym[A])(implicit ov3: Overload3): A = {
    ifThenElse(cond, () => thenBr, () => elseBr)
  }
  @rig def __ifThenElse(cond: Bit, thenBr: => Void, elseBr: => Any)(implicit ov3: Overload4): Void = {
    ifThenElse(cond, () => thenBr, () => { elseBr; void })
  }
  @rig def __ifThenElse(cond: Bit, thenBr: => Any, elseBr: => Void)(implicit ov3: Overload5): Void = {
    ifThenElse(cond, () => { thenBr; void }, () => { elseBr })
  }
  @rig def __ifThenElse(cond: Bit, thenBr: => Void, elseBr: => Void)(implicit ov3: Overload6): Void = {
    ifThenElse(cond, () => { thenBr }, () => { elseBr })
  }

  @rig def ifThenElse[A](cond: Bit, thenBr: () => Sym[A], elseBr: () => Sym[A]): A = {
    val blkThen = stageBlock{ thenBr() }
    val blkElse = stageBlock{ elseBr() }
    implicit val A: Type[A] = blkThen.tp
    stage(IfThenElse[A](cond,blkThen,blkElse))
  }

  @rig def __return(expr: Any): Unit = {
    error(ctx, "return is not yet supported within spatial applications")
    error(ctx)
  }
  @rig def __whileDo(cond: Boolean, body: Unit): Unit = {
    error(ctx, "while loops are not yet supported within spatial applications")
    error(ctx)
  }
  @rig def __doWhile(body: Unit, cond: Boolean): Unit = {
    error(ctx, "do while loops are not yet supported within spatial applications")
    error(ctx)
  }

  def __throw(t: Throwable): Unit = macro forge.EmbeddedControls.throwImpl
}

private object SpatialVirtualization {
  def text_plus(c: whitebox.Context)(x1: c.Expr[String], x2: c.Expr[Any]): c.Expr[Text] = {
    import c.universe._
    c.Expr(q"Text($x1) + $x2")
  }

//  def eqlImpl[T](c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any]): c.Expr[T] = {
//    import c.universe._
//    c.Expr(q"$x1 === $x2")
//  }
//
//  def eqlLiftRightImpl[T](c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any])(l: c.Tree): c.Expr[T] = {
//    import c.universe._
//    c.Expr(q"$x1 === lift($x2)")
//  }
//
//  def eqlLiftLeftImpl[T](c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any])(l: c.Tree): c.Expr[T] = {
//    import c.universe._
//    c.Expr(q"lift($x1) === $x2")
//  }
//
//  def neqImpl(c: whitebox.Context)(a: c.Expr[Any], b: c.Expr[Any]): c.Expr[Bit] = {
//    import c.universe._
//    c.Expr(q"$a !== $b")
//  }
//
//  def neqLiftRightImpl(c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any])(l: c.Tree): c.Expr[Bit] = {
//    import c.universe._
//    c.Expr(q"$x1 !== lift($x2)")
//  }
//
//  def neqLiftLeftImpl[T](c: whitebox.Context)(x1: c.Expr[Any], x2: c.Expr[Any])(l: c.Tree): c.Expr[T] = {
//    import c.universe._
//    c.Expr(q"lift($x1) =!= $x2")
//  }
}