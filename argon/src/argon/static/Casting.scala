package argon
package static

import forge.tags.rig

import scala.annotation.implicitNotFound

trait Casting {
  @implicitNotFound(msg = "Casting from ${A} to ${B} is undefined.")
  type Cast[A,B] = Either[Cast2Way[B,A],CastFunc[A,B]]

  implicit class CastOps[A,B](c: Cast[A,B]) {
    @rig def apply(a: A): B = c match {
      case Left(cast)  => cast.applyLeft(a)
      case Right(cast) => cast(a)
    }
    @rig def applyLeft(b: B): Option[A] = c match {
      case Left(cast)  => cast.get(b)
      case Right(cast) => cast.getLeft(b)
    }
    @rig def unchecked(a: A): B = c match {
      case Left(cast)  => cast.uncheckedLeft(a)
      case Right(cast) => cast.unchecked(a)
    }
    @rig def uncheckedLeft(b: B): Option[A] = c match {
      case Left(cast)  => cast.get(b)
      case Right(cast) => cast.uncheckedGetLeft(b)
    }
  }

  @rig def cast[A,B](a: A)(implicit cast: Cast[A,B]): B = cast.apply(a)

  implicit class SelfType[A<:Ref[_,_]](x: A) {
    def selfType: A = x.tp.asInstanceOf[A]
  }
}
