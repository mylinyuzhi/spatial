package spatial.lang
package static

import argon._
import forge.VarLike
import forge.tags._
import spatial.node._
import utils.Overloads._

import scala.reflect.{ClassTag, classTag}

trait ImplicitsPriority3 {
  implicit def numericCast[A:Num,B:Num]: Cast[A,B] = Right(new CastFunc[A,B]{
    @api def apply(a: A): B = (Num[B] match {
      case tp:Fix[s,i,f] =>
        import tp.fmt._     // This imports implicits for BOOL[s], INT[i], and INT[f]
        a.__toFix[s,i,f]

      case tp:Flt[m,e] =>
        import tp.fmt._     // This imports implicits for INT[m] and INT[e]
        a.__toFlt[m,e]

    }).asInstanceOf[B]
  })


  // --- Any
  implicit class VirtualizeAnyMethods(lhs: Any) {
    @rig def infix_toString(): Text = lhs match {
      case t: Top[_] => t.toText
      case t => Text(t.toString)
    }

    @rig def infix_!=(rhs: Top[_]): Bit = rhs !== rhs.tp.from(lhs)
    @rig def infix_==(rhs: Top[_]): Bit = rhs === rhs.tp.from(lhs)
    def infix_!=(rhs: Any): Boolean = lhs != rhs
    def infix_==(rhs: Any): Boolean = lhs == rhs

    def infix_##(rhs: Any): Int = lhs.##
    def infix_equals(rhs: Any): Boolean = lhs.equals(rhs)
    def infix_hashCode(): Int = lhs.hashCode()
    def infix_asInstanceOf[T](): T = lhs.asInstanceOf[T]
    def infix_isInstanceOf[T:ClassTag](): Boolean = utils.isSubtype(lhs.getClass, classTag[T].runtimeClass)
    def infix_getClass(): Class[_] = lhs.getClass
  }


  // --- String
  import scala.collection.immutable.WrappedString
  implicit def stringToWrappedString(x: String): WrappedString = new WrappedString(x)

  implicit class VirtualizeStringMethods(lhs: String) {
    @rig def infix_+(rhs: Any): Text = rhs match {
      case t: Top[_] => Text(lhs) ++ t.toText
      case t => Text(lhs + t.toString)
    }
  }


  // --- FixPt
  @api implicit def SeriesFromFix[S:BOOL,I:INT,F:INT](x: Fix[S,I,F]): Series[Fix[S,I,F]] = x.toSeries


  // --- A:Type
  // Shadows name in Predef
  implicit class any2stringadd[A:Type](x: A)(implicit ctx: SrcCtx, state: State) {
    def +(y: Any): Text = (x, y) match {
      case (a: Top[_], b: Top[_]) => a.toText ++ b.toText
      case (a, b: Top[_]) => Text(a.toString) ++ b.toText
      case (a: Top[_], b) => a.toText ++ Text(b.toString)
      case (a, b)         => Text(a.toString) ++ Text(b.toString)
    }
  }

  @api implicit def reverseRegLookup[A:Bits](a: A): Reg[A] = a match {
    case Op(RegRead(reg)) => reg.asInstanceOf[Reg[A]]
    case Op(GetReg(reg))  => reg.asInstanceOf[Reg[A]]
    case _ =>
      error(ctx, s"No register available for ${a.nameOr("value")}")
      error(ctx)
      err[Reg[A]]("No register available")
  }


  // Using Lift[A] is always lowest priority
  @rig implicit def liftBoolean(b: Boolean): Lift[Bit] = new Lift(b,b.to[Bit])
  @rig implicit def liftByte(b: Byte): Lift[I8] = new Lift[I8](b,b.to[I8])
  @rig implicit def liftChar(b: Char): Lift[U8] = new Lift[U8](b,b.to[U8])
  @rig implicit def liftShort(b: Short): Lift[I16] = new Lift[I16](b,b.to[I16])
  @rig implicit def liftInt(b: Int): Lift[I32] = new Lift[I32](b,b.to[I32])
  @rig implicit def liftLong(b: Long): Lift[I64] = new Lift[I64](b,b.to[I64])
  @rig implicit def liftFloat(b: Float): Lift[F32] = new Lift[F32](b,b.to[F32])
  @rig implicit def liftDouble(b: Double): Lift[F64] = new Lift[F64](b,b.to[F64])
}

trait ImplicitsPriority2 extends ImplicitsPriority3 {
  implicit def boxNum[A:Num](x: A): Num[A] = Num[A].box(x)

  import scala.runtime.{RichInt,RichChar,RichByte,RichBoolean,RichShort,RichLong}
  import scala.collection.immutable.StringOps
  implicit def boolean2RichBoolean(x: Boolean): RichBoolean = new RichBoolean(x)
  implicit def char2RichChar(x: Char): RichChar = new RichChar(x)
  implicit def byte2RichByte(x: Byte): RichByte = new RichByte(x)
  implicit def short2RichShort(x: Short): RichShort = new RichShort(x)
  implicit def int2RichInt(x: Int): RichInt = new RichInt(x)
  implicit def long2RichLong(x: Long): RichLong = new RichLong(x)
  implicit def stringToStringOps(x: String): StringOps = new StringOps(x)

  // Using Lift[A] is always lowest priority
  @rig implicit def litBoolean(b: Boolean): Literal = new Literal(b)
  @rig implicit def litByte(b: Byte): Literal = new Literal(b)
  @rig implicit def litShort(b: Short): Literal = new Literal(b)
  @rig implicit def litInt(b: Int): Literal = new Literal(b)
  @rig implicit def litLong(b: Long): Literal = new Literal(b)
  @rig implicit def litFloat(b: Float): Literal = new Literal(b)
  @rig implicit def litDouble(b: Double): Literal = new Literal(b)
}

trait ImplicitsPriority1 extends ImplicitsPriority2 {
  implicit def boxBits[A:Bits](x: A): Bits[A] = Bits[A].box(x)
  implicit def boxOrder[A:Order](x: A): Order[A] = Order[A].box(x)
  implicit def boxArith[A:Arith](x: A): Arith[A] = Arith[A].box(x)


  implicit def selfCast[A:Type]: Cast[A,A] = Right(new CastFunc[A,A] {
    @rig def apply(a: A): A = a
  })

  // Shadows Predef method
  @api implicit def wrapString(x: String): Text = Text(x)

}

trait Implicits extends ImplicitsPriority1 { this: SpatialStatics =>
  implicit def box[A:Type](x: A): Top[A] = Type[A].boxed(x).asInstanceOf[Top[A]]
  implicit class BoxSym[A:Type](x: A) extends argon.static.ExpMiscOps[Any,A](x)

  def * = new Wildcard

  // Ways to lift type U to type S:
  //   1. Implicit conversions:
  //        a + 1
  //   1.a. Implicit wrapping:
  //          0 until 10
  //   2. Lifting with no evidence: Lift[U,S]
  //        if (c) 0 else 1: Use
  //   3. Explicit lifting: Cast[U,S]
  //        1.to[I32]


  //=== Bit ===//
  class Cvt_Text_Bit extends Cast2Way[Text,Bit] {
    @rig def apply(x: Text): Bit = stage(TextToBit(x))
    @rig def applyLeft(x: Bit): Text = stage(BitToText(x))
  }
  implicit lazy val CastTextToBit: Cast[Text,Bit] = Right(new Cvt_Text_Bit)
  implicit lazy val CastBitToText: Cast[Bit,Text] = Left(new Cvt_Text_Bit)

  class Cvt_Bit_Fix[S:BOOL,I:INT,F:INT] extends Cast2Way[Bit,Fix[S,I,F]] {
    @rig def apply(x: Bit): Fix[S,I,F] = mux(x, 1.to[Fix[S,I,F]], 0.to[Fix[S,I,F]])
    @rig def applyLeft(x: Fix[S,I,F]): Bit = x !== 0
  }
  implicit def CastBitToFix[S:BOOL,I:INT,F:INT]: Cast[Bit,Fix[S,I,F]] = Right(new Cvt_Bit_Fix[S,I,F])
  implicit def CastFixToBit[S:BOOL,I:INT,F:INT]: Cast[Fix[S,I,F],Bit] = Left(new Cvt_Bit_Fix[S,I,F])

  //=== Fix ===//

  class Cvt_Fix_Fix[S1:BOOL,I1:INT,F1:INT,S2:BOOL,I2:INT,F2:INT] extends CastFunc[Fix[S1,I1,F1],Fix[S2,I2,F2]] {
    @rig def apply(x: Fix[S1,I1,F1]): Fix[S2,I2,F2] = stage(FixToFix(x, FixFmt.from[S2,I2,F2]))
    @rig override def getLeft(x: Fix[S2,I2,F2]): Option[Fix[S1,I1,F1]] = Some(stage(FixToFix(x, FixFmt.from[S1,I1,F1])))
  }
  implicit def CastFixToFix[S1:BOOL,I1:INT,F1:INT,S2:BOOL,I2:INT,F2:INT]: Cast[Fix[S1,I1,F1],Fix[S2,I2,F2]] = {
    Right(new Cvt_Fix_Fix[S1,I1,F1,S2,I2,F2])
  }

  class Cvt_Text_Fix[S:BOOL,I:INT,F:INT] extends Cast2Way[Text,Fix[S,I,F]] {
    @rig def apply(x: Text): Fix[S,I,F] = stage(TextToFix(x,FixFmt.from[S,I,F]))
    @rig def applyLeft(x: Fix[S,I,F]): Text = stage(FixToText(x))
  }
  implicit def CastTextToFix[S:BOOL,I:INT,F:INT]: Cast[Text,Fix[S,I,F]] = Right(new Cvt_Text_Fix[S,I,F])
  implicit def CastFixToText[S:BOOL,I:INT,F:INT]: Cast[Fix[S,I,F],Text] = Left(new Cvt_Text_Fix[S,I,F])

  //=== Flt ===//

  class Cvt_Flt_Flt[M1:INT,E1:INT,M2:INT,E2:INT] extends CastFunc[Flt[M1,E1],Flt[M2,E2]] {
    @rig def apply(x: Flt[M1,E1]): Flt[M2,E2] = stage(FltToFlt(x, FltFmt.from[M2,E2]))
    @rig override def getLeft(x: Flt[M2,E2]): Option[Flt[M1,E1]] = Some(stage(FltToFlt(x, FltFmt.from[M1,E1])))
  }
  implicit def CastFltToFlt[M1:INT,E1:INT,M2:INT,E2:INT]: Cast[Flt[M1,E1],Flt[M2,E2]] = {
    Right(new Cvt_Flt_Flt[M1,E1,M2,E2])
  }

  class Cvt_Text_Flt[M:INT,E:INT] extends Cast2Way[Text,Flt[M,E]] {
    @rig def apply(x: Text): Flt[M,E] = stage(TextToFlt(x,FltFmt.from[M,E]))
    @rig def applyLeft(x: Flt[M,E]): Text = stage(FltToText(x))
  }
  implicit def CastTextToFlt[M:INT,E:INT]: Cast[Text,Flt[M,E]] = Right(new Cvt_Text_Flt[M,E])
  implicit def CastFltToText[M:INT,E:INT]: Cast[Flt[M,E],Text] = Left(new Cvt_Text_Flt[M,E])


  class Cvt_Fix_Flt[S1:BOOL,I1:INT,F1:INT,M2:INT,E2:INT] extends Cast2Way[Fix[S1,I1,F1],Flt[M2,E2]] {
    @rig def apply(a: Fix[S1,I1,F1]): Flt[M2,E2] = stage(FixToFlt(a,FltFmt.from[M2,E2]))
    @rig def applyLeft(b: Flt[M2,E2]): Fix[S1,I1,F1] = stage(FltToFix(b,FixFmt.from[S1,I1,F1]))
  }
  implicit def CastFixToFlt[S1:BOOL,I1:INT,F1:INT,M2:INT,E2:INT]: Cast[Fix[S1,I1,F1],Flt[M2,E2]] = Right(new Cvt_Fix_Flt[S1,I1,F1,M2,E2])
  implicit def CastFltToFix[M1:INT,E1:INT,S2:BOOL,I2:INT,F2:INT]: Cast[Flt[M1,E1],Fix[S2,I2,F2]] = Left(new Cvt_Fix_Flt[S2,I2,F2,M1,E1])


  // --- Implicit Conversions

  class CastType[A](x: A) {
    @api def to[B](implicit cast: Cast[A,B]): B = cast.apply(x)
  }

  class RegNumerics[A:Num](reg: Reg[A])(implicit ctx: SrcCtx, state: State) {
    def :+=(data: A): Void = reg := reg.value + data.unbox
    def :-=(data: A): Void = reg := reg.value - data.unbox
    def :*=(data: A): Void = reg := reg.value * data.unbox
  }


  class LiteralWrapper[A](a: A)(implicit ctx: SrcCtx, state: State) {
    def to[B](implicit cast: Cast[A,B]): B = cast(a)
    def toUnchecked[B](implicit cast: Cast[A,B]): B = cast.unchecked(a)
  }

  // Note: Naming is important here to override the names in Predef.scala
  // Note: Need the ctx and state at the implicit class to avoid issues with currying
  class BooleanWrapper(a: Boolean)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Boolean](a)
  class ByteWrapper(a: Byte)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Byte](a)
  class CharWrapper(a: Char)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Char](a)
  class ShortWrapper(a: Short)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Short](a)

  class IntWrapper(b: Int)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Int](b) {
    def until(end: I32): Series[I32] = Series[I32](I32(b), end, I32(1), I32(1))
    def by(step: I32): Series[I32] = Series[I32](1, b, step, 1)
    def par(p: I32): Series[I32] = Series[I32](1, b, 1, p)

    def until(end: Int): Series[I32] = Series[I32](b, end, 1, 1)
    def by(step: Int): Series[I32] = Series[I32](1, b, step, 1)
    def par(p: Int): Series[I32] = Series[I32](1, b, 1, p)

    def ::(start: I32): Series[I32] = Series[I32](start, b, 1, 1)
    def ::(start: Int): Series[I32] = Series[I32](start, b, 1, 1)

    /**
      * Creates a parameter with this value as the default, and the given range with a stride of 1.
      *
      * ``1 (1 -> 5)``
      * creates a parameter with a default of 1 with a range [1,5].
      */
    def apply(range: (Int, Int))(implicit ov1: Overload0): I32 = createParam(b, range._1, 1, range._2)
    /**
      * Creates a parameter with this value as the default, and the given strided range.
      *
      * ``1 (1 -> 2 -> 8)``
      * creates a parameter with a default of 1 with a range in [2,8] with step of 4.
      */
    def apply(range: ((Int, Int), Int))(implicit ov2: Overload1): I32 = createParam(b, range._1._1, range._1._2, range._2)

    def to(end: Int): Range = Range.inclusive(b, end)

    def x: I32 = this.to[I32]
  }

  class LongWrapper(a: Long)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Long](a)
  class FloatWrapper(a: Float)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Float](a)
  class DoubleWrapper(a: Double)(implicit ctx: SrcCtx, state: State) extends LiteralWrapper[Double](a)


  // --- A (Any)
  implicit def castType[A](a: A): CastType[A] = new CastType[A](a)


  // --- Reg[A]
  @api implicit def regRead[A](x: Reg[A]): A = x.value
  @api implicit def regNumerics[A:Num](x: Reg[A]): RegNumerics[A] = new RegNumerics[A](x)


  // --- Wildcard
  @api implicit def wildcardToForever(w: Wildcard): Counter[I32] = stage(ForeverNew())


  // --- Series
  @api implicit def SeriesToCounter[S:BOOL,I:INT,F:INT](x: Series[Fix[S,I,F]]): Counter[Fix[S,I,F]] = Counter.from(x)


  // --- String
  @api implicit def augmentString(x: String): Text = Text(x)


  // --- Unit
  @api implicit def VoidFromUnit(c: Unit): Void = Void.c


  // --- VarLike
  @api implicit def varRead[A](v: VarLike[A]): A = v.__read


  // --- Boolean
  implicit lazy val castBooleanToBit: Cast[Boolean,Bit] = Right(new Lifter[Boolean,Bit])
  implicit def CastBooleanToFix[S:BOOL,I:INT,F:INT]: Cast[Boolean,Fix[S,I,F]] = Right(new Lifter[Boolean,Fix[S,I,F]])
  implicit def CastBooleanToFlt[M:INT,E:INT]: Cast[Boolean,Flt[M,E]] = Right(new Lifter[Boolean,Flt[M,E]])
  implicit def CastBooleanToNum[A:Num]: Cast[Boolean,A] = Right(new Lifter[Boolean,A])

  @api implicit def BitFromBoolean(c: Boolean): Bit = c.to[Bit]
  @api implicit def booleanWrapper(c: Boolean): BooleanWrapper = new BooleanWrapper(c)

  // --- Byte
  implicit lazy val castByteToBit: Cast[Byte,Bit] = Right(new Lifter[Byte,Bit])
  implicit def CastByteToFix[S:BOOL,I:INT,F:INT]: Cast[Byte,Fix[S,I,F]] = Right(new Lifter[Byte,Fix[S,I,F]])
  implicit def CastByteToFlt[M:INT,E:INT]: Cast[Byte,Flt[M,E]] = Right(new Lifter[Byte,Flt[M,E]])
  implicit def CastByteToNum[A:Num]: Cast[Byte,A] = Right(new Lifter[Byte,A])

  @api implicit def FixFromByte[S:BOOL,I:INT,F:INT](c: Byte): Fix[S,I,F] = c.to[Fix[S,I,F]]
  @api implicit def FltFromByte[M:INT,E:INT](c: Byte): Flt[M,E] = c.to[Flt[M,E]]
  @api implicit def NumFromByte[A:Num](c: Byte): A = c.to[A]
  @api implicit def byteWrapper(c: Byte): ByteWrapper = new ByteWrapper(c)


  // --- Char
  implicit lazy val castCharToBit: Cast[Char,Bit] = Right(new Lifter[Char,Bit])
  implicit def CastCharToFix[S:BOOL,I:INT,F:INT]: Cast[Char,Fix[S,I,F]] = Right(new Lifter[Char,Fix[S,I,F]])
  implicit def CastCharToFlt[M:INT,E:INT]: Cast[Char,Flt[M,E]] = Right(new Lifter[Char,Flt[M,E]])
  implicit def CastCharToNum[A:Num]: Cast[Char,A] = Right(new Lifter[Char,A])

  @api implicit def FixFromChar[S:BOOL,I:INT,F:INT](c: Char): Fix[S,I,F] = c.to[Fix[S,I,F]]
  @api implicit def FltFromChar[M:INT,E:INT](c: Char): Flt[M,E] = c.to[Flt[M,E]]
  @api implicit def NumFromChar[A:Num](c: Char): A = c.to[A]
  @api implicit def charWrapper(c: Char): CharWrapper = new CharWrapper(c)


  // --- Short
  implicit lazy val castShortToBit: Cast[Short,Bit] = Right(new Lifter[Short,Bit])
  implicit def CastShortToFix[S:BOOL,I:INT,F:INT]: Cast[Short,Fix[S,I,F]] = Right(new Lifter[Short,Fix[S,I,F]])
  implicit def CastShortToFlt[M:INT,E:INT]: Cast[Short,Flt[M,E]] = Right(new Lifter[Short,Flt[M,E]])
  implicit def CastShortToNum[A:Num]: Cast[Short,A] = Right(new Lifter[Short,A])

  @api implicit def FltFromShort[M:INT,E:INT](c: Short): Flt[M,E] = c.to[Flt[M,E]]
  @api implicit def FixFromShort[S:BOOL,I:INT,F:INT](c: Short): Fix[S,I,F] = c.to[Fix[S,I,F]]
  @api implicit def NumFromShort[A:Num](c: Short): A = c.to[A]
  @api implicit def ShortWrapper(c: Short): ShortWrapper = new ShortWrapper(c)


  // --- Int
  implicit lazy val castIntToBit: Cast[Int,Bit] = Right(new Lifter[Int,Bit])
  implicit def CastIntToFix[S:BOOL,I:INT,F:INT]: Cast[Int,Fix[S,I,F]] = Right(new Lifter[Int,Fix[S,I,F]])
  implicit def CastIntToFlt[M:INT,E:INT]: Cast[Int,Flt[M,E]] = Right(new Lifter[Int,Flt[M,E]])
  implicit def CastIntToNum[A:Num]: Cast[Int,A] = Right(new Lifter[Int,A])

  @api implicit def FltFromInt[M:INT,E:INT](c: Int): Flt[M,E] = c.to[Flt[M,E]]
  @api implicit def FixFromInt[S:BOOL,I:INT,F:INT](c: Int): Fix[S,I,F] = c.to[Fix[S,I,F]]
  @api implicit def NumFromInt[A:Num](c: Int): A = c.to[A]
  @api implicit def intWrapper(c: Int): IntWrapper = new IntWrapper(c)


  // --- Long
  implicit lazy val castLongToBit: Cast[Long,Bit] = Right(new Lifter[Long,Bit])
  implicit def CastLongToFix[S:BOOL,I:INT,F:INT]: Cast[Long,Fix[S,I,F]] = Right(new Lifter[Long,Fix[S,I,F]])
  implicit def CastLongToFlt[M:INT,E:INT]: Cast[Long,Flt[M,E]] = Right(new Lifter[Long,Flt[M,E]])
  implicit def CastLongToNum[A:Num]: Cast[Long,A] = Right(new Lifter[Long,A])

  @api implicit def FixFromLong[S:BOOL,I:INT,F:INT](c: Long): Fix[S,I,F] = c.to[Fix[S,I,F]]
  @api implicit def FltFromLong[M:INT,E:INT](c: Long): Flt[M,E] = c.to[Flt[M,E]]
  @api implicit def NumFromLong[A:Num](c: Long): A = c.to[A]
  @api implicit def longWrapper(c: Long): LongWrapper = new LongWrapper(c)


  // --- Float
  implicit lazy val castFloatToBit: Cast[Float,Bit] = Right(new Lifter[Float,Bit])
  implicit def CastFloatToFix[S:BOOL,I:INT,F:INT]: Cast[Float,Fix[S,I,F]] = Right(new Lifter[Float,Fix[S,I,F]])
  implicit def CastFloatToFlt[M:INT,E:INT]: Cast[Float,Flt[M,E]] = Right(new Lifter[Float,Flt[M,E]])
  implicit def CastFloatToNum[A:Num]: Cast[Float,A] = Right(new Lifter[Float,A])

  @api implicit def FixFromFloat[S:BOOL,I:INT,F:INT](c: Float): Fix[S,I,F] = c.to[Fix[S,I,F]]
  @api implicit def FltFromFloat[M:INT,E:INT](c: Float): Flt[M,E] = c.to[Flt[M,E]]
  @api implicit def NumFromFloat[A:Num](c: Float): A = c.to[A]
  @api implicit def floatWrapper(c: Float): FloatWrapper = new FloatWrapper(c)


  // --- Double
  implicit lazy val castDoubleToBit: Cast[Double,Bit] = Right(new Lifter[Double,Bit])
  implicit def CastDoubleToFix[S:BOOL,I:INT,F:INT]: Cast[Double,Fix[S,I,F]] = Right(new Lifter[Double,Fix[S,I,F]])
  implicit def CastDoubleToFlt[M:INT,E:INT]: Cast[Double,Flt[M,E]] = Right(new Lifter[Double,Flt[M,E]])
  implicit def CastDoubleToNum[A:Num]: Cast[Double,A] = Right(new Lifter[Double,A])

  @api implicit def FixFromDouble[S:BOOL,I:INT,F:INT](c: Double): Fix[S,I,F] = c.to[Fix[S,I,F]]
  @api implicit def FltFromDouble[M:INT,E:INT](c: Double): Flt[M,E] = c.to[Flt[M,E]]
  @api implicit def NumFromDouble[A:Num](c: Double): A = c.to[A]
  @api implicit def doubleWrapper(c: Double): DoubleWrapper = new DoubleWrapper(c)

}
