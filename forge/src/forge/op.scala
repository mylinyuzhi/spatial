package forge

import scala.annotation.StaticAnnotation
import scala.reflect.macros.blackbox
import scala.language.experimental.macros

final class op extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro op.impl
}

object op {
  def impl(c: blackbox.Context)(annottees: c.Tree*): c.Tree = {
    val util = utils[c.type](c)
    import c.universe._
    import util._

    val tree = annottees.head match {
      case cls: ClassDef =>
        val name = cls.name
        val names = cls.constructorArgs.head.map(_.name)
        val fnames = names.map{name => q"f($name)" }
        val updates = names.zip(fnames).map{case (name,fname) => q"$name = $fname" }

        cls.asCaseClass.withVarParams
           .injectMethod {(_,_) =>
             q"override def mirror(f:Tx) = new $name(..$fnames)"
           }
           .injectMethod{(_,_) =>
             q"override def update(f:Tx) = { ..$updates }"
           }

      case t =>
        c.error(c.enclosingPosition, "@mod can only be used on class definitions")
        t
    }
    c.info(c.enclosingPosition, showCode(tree), force = true)
    tree
  }
}