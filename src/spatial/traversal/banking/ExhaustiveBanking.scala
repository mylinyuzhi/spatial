package spatial.traversal.banking

import argon._
import utils.implicits.collections._
import utils.math.isPow2
import poly.{ConstraintMatrix, ISL, SparseMatrix, SparseVector}

import spatial.data._
import spatial.lang._

case class ExhaustiveBanking()(implicit IR: State, isl: ISL) extends BankingStrategy {
  // TODO[4]: What should the cutoff be for starting with powers of 2 versus exact accesses?
  private val MAGIC_CUTOFF_N = 1.4
  private val k = boundVar[I32]
  private val k0 = boundVar[I32]
  private val k1 = boundVar[I32]
  private val Bs = Seq(2, 4, 8, 16, 32, 64, 128, 256)

  override def bankAccesses(
    mem:    Sym[_],
    rank:   Int,
    reads:  Set[Set[AccessMatrix]],
    writes: Set[Set[AccessMatrix]],
    dimGrps: Seq[Seq[Seq[Int]]]
  ): Seq[Seq[Banking]] = {

    val grps = (reads ++ writes).map(_.toSeq.map(_.matrix))
    if (grps.forall(_.lengthLessThan(2))) Seq(Seq(ModBanking.Unit(rank)))
    else {
      dimGrps.flatMap{strategy: Seq[Seq[Int]] =>
        dbg(s"  Trying dimension group {${strategy.mkString(",")}}")
        val banking = strategy.map{dims =>
          val selGrps = grps.map{grp => grp.map{mat => mat.sliceDims(dims)} }
          findBanking(selGrps, dims)
        }
        if (isValidBanking(banking,grps)) Some(banking) else None
      }
    }
  }

  /** True if this is a valid banking strategy for the given sets of access matrices. */
  def isValidBanking(banking: Seq[ModBanking], grps: Set[Seq[SparseMatrix[Idx]]]): Boolean = {
    // TODO[2]: This may not be correct in all cases, need to verify!
    val banks = banking.map(_.nBanks).product
    grps.forall{_.lengthLessThan(banks+1)}
  }

  /**
    * Generates an iterator over all vectors of given rank, with values up to N
    * Prioritizes vectors which are entirely powers of 2 first
    * Note that the total size is O(N**rank)
    */
  def Alphas(rank: Int, N: Int): Iterator[Seq[Int]] = {
    val a2 = (0 to 2*N).filter(x => isPow2(x) || x == 1 || x == 0)
    def Alphas2(dim: Int, prev: Seq[Int]): Iterator[Seq[Int]] = {
      if (dim < rank) {
        a2.iterator.flatMap{aD => Alphas2(dim+1, prev :+ aD) }
      }
      else a2.iterator.map{aR => prev :+ aR }
    }
    def AlphasX(dim: Int, prev: Seq[Int]): Iterator[Seq[Int]] = {
      if (dim < rank) {
        (0 to 2*N).iterator.flatMap{aD => AlphasX(dim+1, prev :+ aD) }
      }
      else (0 to 2*N).iterator.map{aR => prev :+ aR }.filterNot(_.forall(x => isPow2(x) || x == 1))
    }
    Alphas2(1, Nil).filterNot(_.forall(_ == 0)) ++ AlphasX(1, Nil).filterNot(_.forall(_ == 0))
  }

  protected def findBanking(grps: Set[Seq[SparseMatrix[Idx]]], dims: Seq[Int]): ModBanking = {
    val rank = dims.length
    val Nmin: Int = grps.map(_.size).maxOrElse(1)

    val (n2,nx) = (Nmin to 8*Nmin).partition{i => isPow2(i) }
    val n2Head = if (n2.head.toDouble/Nmin > MAGIC_CUTOFF_N) Seq(Nmin) else Nil
    val Ns = (n2Head ++ n2 ++ nx).iterator

    var banking: Option[ModBanking] = None

    while(Ns.hasNext && banking.isEmpty) {
      val N = Ns.next()
      val As = Alphas(rank, N)
      while (As.hasNext && banking.isEmpty) {
        val alpha = As.next()
        if (checkCyclic(N,alpha,grps)) banking = Some(ModBanking(N,1,alpha,dims))
        else {
          val B = Bs.find{b => checkBlockCyclic(N,b,alpha,grps) }
          banking = B.map{b => ModBanking(N, b, alpha, dims) }
        }
      }
    }

    banking.getOrElse(ModBanking.Unit(rank))
  }

  implicit class SeqMath(a: Seq[Int]) {
    def *(b: SparseMatrix[Idx]): SparseVector[Idx] = {
      val vec = b.keys.mapping{k => b.rows.zip(a).iterator.map{case (row_i,a_i) => row_i(k)*a_i }.sum }
      val c = b.rows.zip(a).iterator.map{case (row_i,a_i) => row_i.c*a_i}.sum
      SparseVector[Idx](vec,c,Map.empty)
    }
  }

  private def checkCyclic(N: Int, alpha: Seq[Int], grps: Set[Seq[SparseMatrix[Idx]]]): Boolean = grps.forall{_.forallPairs{(a0,a1) =>
    val c0 = (alpha*(a0 - a1) + (k,N)) === 0
    c0.andDomain.isEmpty
  }}

  private def checkBlockCyclic(N: Int, B: Int, alpha: Seq[Int], grps: Set[Seq[SparseMatrix[Idx]]]): Boolean = grps.forall{_.forallPairs{(a0,a1) =>
    val alphaA0 = alpha*a0
    val alphaA1 = alpha*a1
    val c0 = (-alphaA0 + (k0,B*N) + (k1,B) + B - 1) >== 0
    val c1 = (-alphaA1 + (k1,B) + B - 1) >== 0
    val c2 = (alphaA0 - (k0,B*N) - (k1,B)) >== 0
    val c3 = (alphaA1 - (k1,B)) >== 0
    ConstraintMatrix(Set(c0,c1,c2,c3)).andDomain.isEmpty
  }}
}
