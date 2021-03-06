// See LICENSE.txt for license details.
package templates

import chisel3._
import chisel3.iotesters.{PeekPokeTester, Driver, ChiselFlatSpec}
import chisel3.testers.BasicTester
import org.scalatest._
import org.scalatest.prop._

/**
 * Mem1D test harness
 */
class Mem1DTests(c: Mem1D) extends PeekPokeTester(c) {
  step(1)
  reset(1)
  for (i <- 0 until c.size ) {
    poke(c.io.w.ofs, i)
    poke(c.io.w.data, i*2)
    poke(c.io.w.en, 1)
    poke(c.io.wMask, 1)
    step(1) 
    poke(c.io.w.en, 0)
    poke(c.io.wMask, 0)
    step(1)
  }

  for (i <- 0 until c.size ) {
    poke(c.io.r.ofs, i)
    poke(c.io.r.en, 1)
    poke(c.io.rMask, 1)
    step(1)
    expect(c.io.output.data, i*2)
    poke(c.io.r.en, 0)
    poke(c.io.rMask, 0)
    step(1)
  }

}

class FFTests(c: FF) extends PeekPokeTester(c) {
  val initval = 10
  poke(c.io.input(0).init, initval)
  step(1)
  reset(1)
  expect(c.io.output.data, initval)

  // overwrite init
  poke(c.io.input(0).data, 0)
  poke(c.io.input(0).en, 1)
  step(1)
  expect(c.io.output.data, 0)
  step(1)

  val numCycles = 15
  for (i <- 0 until numCycles) {
    val newenable = rnd.nextInt(2)
    val oldout = peek(c.io.output.data)
    poke(c.io.input(0).data, i)
    poke(c.io.input(0).en, newenable)
    step(1)
    if (newenable == 1) {
      // val a = peek(c.io.output.data)
      // println(s"expect $a to be $i")
      expect(c.io.output.data, i)
    } else {
      // val a = peek(c.io.output.data)
      // println(s"expect $a to be $oldout")
      expect(c.io.output.data, oldout)
    }
  }
  poke(c.io.input(0).reset, 1)
  poke(c.io.input(0).en, 0)
  // val b = peek(c.io.output.data)
  // println(s"expect $b to be $initval")
  expect(c.io.output.data, initval)
  step(1)
  // val cc = peek(c.io.output.data)
  // println(s"expect $cc to be $initval")
  expect(c.io.output.data, initval)
  poke(c.io.input(0).reset, 0)
  step(1)
  // val d = peek(c.io.output.data)
  // println(s"expect $d to be $initval")
  expect(c.io.output.data, initval)
}


/**
 * SRAM test harness
 */
class SRAMTests(c: SRAM) extends PeekPokeTester(c) {
  val depth = c.logicalDims.reduce{_*_}
  val N = c.logicalDims.length

  reset(1)

  // Write to each address
  val wPar = c.directWMux.values.toList.head.length
  for (i <- 0 until c.logicalDims(0) by c.banks(0)) { // Each row
    for (j <- 0 until c.logicalDims(1) by c.banks(1)) {
      // Set addrs
      (0 until c.banks(0)).foreach{ ii => (0 until c.banks(1)).foreach{ jj =>
        val kdim = ii * c.banks(1) + jj
        // poke(c.io.directW(kdim).banks(0), i % c.banks(0))
        // poke(c.io.directW(kdim).banks(1), (j+kdim) % c.banks(1))
        poke(c.io.directW(kdim).ofs, (i+ii) / c.banks(0) * (c.logicalDims(1) / c.banks(1)) + (j+jj) / c.banks(1))
        poke(c.io.directW(kdim).data, (i*c.logicalDims(0) + j + kdim)*2)
        poke(c.io.directW(kdim).en, true)
      }}
      step(1)
    }
  }
  // Turn off wEn
  (0 until wPar).foreach{ kdim => 
    poke(c.io.directW(kdim).en, false)
  }

  step(30)

  // Check each address
  val rPar = c.directRMux.values.toList.head.length
  for (i <- 0 until c.logicalDims(0) by c.banks(0)) { // Each row
    for (j <- 0 until c.logicalDims(1) by c.banks(1)) {
      // Set addrs
      (0 until c.banks(0)).foreach{ ii => (0 until c.banks(1)).foreach{ jj =>
        val kdim = ii * c.banks(1) + jj
        // poke(c.io.directR(kdim).banks(0), i % c.banks(0))
        // poke(c.io.directR(kdim).banks(1), (j+kdim) % c.banks(1))
        poke(c.io.directR(kdim).ofs, (i+ii) / c.banks(0) * (c.logicalDims(1) / c.banks(1)) + (j+jj) / c.banks(1))
        poke(c.io.directR(kdim).en, true)
      }}
      step(1)
      (0 until rPar).foreach { kdim => 
        expect(c.io.output.data(kdim), (i*c.logicalDims(0) + j + kdim)*2)
      }
    }
  }
  // Turn off rEn
  (0 until rPar).foreach{ reader => 
    poke(c.io.directR(reader).en, false)
  }

  step(1)


}


/**
 * SRAM test harness
 */
class NBufMemTests(c: NBufMem) extends PeekPokeTester(c) {

  val depth = c.logicalDims.reduce{_*_}
  val N = c.logicalDims.length

  reset(1)

  // Broadcast
  c.mem match {
    case SRAMType => 
      for (i <- 0 until c.logicalDims(0) by c.banks(0)) {
        for (j <- 0 until c.logicalDims(1) by c.banks(1)) {
          (0 until c.banks(0)).foreach{ ii => (0 until c.banks(1)).foreach{ jj =>
            poke(c.io.broadcast(0).banks(0), ii)
            poke(c.io.broadcast(0).banks(1), jj)
            poke(c.io.broadcast(0).ofs, (i+ii) / c.banks(0) * (c.logicalDims(1) / c.banks(1)) + (j+jj) / c.banks(1))
            poke(c.io.broadcast(0).data, 999)
            poke(c.io.broadcast(0).en, true)
            step(1)      
          }}
        }
      }
    case FFType => 
      poke(c.io.broadcast(0).data, 999)
      poke(c.io.broadcast(0).en, true)
      step(1)
  }
  c.io.broadcast.foreach{p => poke(p.en, false)}
  step(1)

  // Read all bufs
  for (buf <- 0 until c.numBufs) {
    c.mem match {
      case SRAMType => 
        val rPar = c.directRMux.values.toList.head.values.toList.flatten.length
        val base = c.directRMux.keys.toList.head * rPar
        for (i <- 0 until c.logicalDims(0) by c.banks(0)) { // Each row
          for (j <- 0 until c.logicalDims(1) by c.banks(1)) {
            // Set addrs
            (0 until c.banks(0)).foreach{ ii => (0 until c.banks(1)).foreach{ jj =>
              val kdim = ii * c.banks(1) + jj
              // poke(c.io.directR(kdim).banks(0), i % c.banks(0))
              // poke(c.io.directR(kdim).banks(1), (j+kdim) % c.banks(1))
              poke(c.io.directR(kdim).ofs, (i+ii) / c.banks(0) * (c.logicalDims(1) / c.banks(1)) + (j+jj) / c.banks(1))
              poke(c.io.directR(kdim).en, true)
            }}
            step(1)
            (0 until rPar).foreach { kdim => 
              expect(c.io.output.data(kdim), 999)
            }
          }
        }  
      case FFType => 
        val base = c.xBarRMux.keys.toList.head
        expect(c.io.output.data(0), 999)
        step(1)
    }
    c.io.directR.foreach{p => poke(p.en, false)}
    // Rotate buffer
    poke(c.io.sEn(0), 1)
    step(1)
    poke(c.io.sDone(0), 1)
    step(1)
    poke(c.io.sEn(0), 0)
    poke(c.io.sDone(0), 0)
    step(1)
  }

  step(20)


  for (epoch <- 0 until c.numBufs*2) {
    c.mem match {
      case SRAMType => 
        // Write to each address
        val wPar = c.directWMux.values.toList.head.values.toList.flatten.length
        for (i <- 0 until c.logicalDims(0) by c.banks(0)) { // Each row
          for (j <- 0 until c.logicalDims(1) by c.banks(1)) {
            // Set addrs
            (0 until c.banks(0)).foreach{ ii => (0 until c.banks(1)).foreach{ jj =>
              val kdim = ii * c.banks(1) + jj
              // poke(c.io.directW(kdim).banks(0), i % c.banks(0))
              // poke(c.io.directW(kdim).banks(1), (j+kdim) % c.banks(1))
              poke(c.io.directW(kdim).ofs, (i+ii) / c.banks(0) * (c.logicalDims(1) / c.banks(1)) + (j+jj) / c.banks(1))
              poke(c.io.directW(kdim).data, epoch*100 + (i*c.logicalDims(0) + j + kdim)*2)
              poke(c.io.directW(kdim).en, 1)
            }}
            step(1)
          }
        }
      case FFType => 
        poke(c.io.xBarW(0).data, epoch*100)
        poke(c.io.xBarW(0).en, true)
        step(1)
    }

    // Turn off wEn
    c.io.directW.foreach{p => poke(p.en, false)}
    c.io.xBarW.foreach{p => poke(p.en, false)}

    step(30)

    // Assume write to buffer 0, read from buffer c.numBufs-1, so do the swapping
    for (i <- 0 until c.numBufs-1){
      // Rotate buffer
      poke(c.io.sEn(0), 1)
      step(1)
      poke(c.io.sDone(0), 1)
      step(1)
      poke(c.io.sEn(0), 0)
      poke(c.io.sDone(0), 0)
      step(1)
    }

    // Check each address
    c.mem match {
      case SRAMType => 
        val rPar = c.directRMux.values.toList.head.values.toList.flatten.length
        for (i <- 0 until c.logicalDims(0) by c.banks(0)) { // Each row
          for (j <- 0 until c.logicalDims(1) by c.banks(1)) {
            // Set addrs
            (0 until c.banks(0)).foreach{ ii => (0 until c.banks(1)).foreach{ jj =>
              val kdim = ii * c.banks(1) + jj
              // poke(c.io.directR(kdim).banks(0), i % c.banks(0))
              // poke(c.io.directR(kdim).banks(1), (j+kdim) % c.banks(1))
              poke(c.io.directR(kdim).ofs, (i+ii) / c.banks(0) * (c.logicalDims(1) / c.banks(1)) + (j+jj) / c.banks(1))
              poke(c.io.directR(kdim).en, 1)
            }}
            step(1)
            (0 until rPar).foreach { kdim => 
              expect(c.io.output.data(kdim), epoch*100 + (i*c.logicalDims(0) + j + kdim)*2)
            }
          }
        }
      case FFType => 
        val base = c.xBarRMux.keys.toList.head
        expect(c.io.output.data(0), epoch*100)
        step(1)
    }
    
    // Turn off rEn
    c.io.directR.foreach{p => poke(p.en, false)}

    step(1)
  }
  


  step(5)
}


class FIFOTests(c: FIFO) extends PeekPokeTester(c) {
  reset(1)
  step(5)

  var fifo = scala.collection.mutable.Queue[Int]()
  def enq(datas: Seq[Int], ens: Seq[Int]) {
    (0 until datas.length).foreach { i => poke(c.io.xBarW(i).data, datas(i)) }
    (0 until datas.length).foreach { i => poke(c.io.xBarW(i).en, ens(i)) }
    step(1)
    (0 until datas.length).foreach { i => poke(c.io.xBarW(i).en, 0) }
    step(1)
    (0 until datas.length).foreach{i => if (ens(i) != 0) fifo.enqueue(datas(i))}
  }
  def deq(ens: Seq[Int]) {
    (0 until ens.length).foreach { i => poke(c.io.xBarR(i).en, ens(i)) }
    val num_popping = ens.reduce{_+_}
    (0 until ens.length).foreach{i => 
      val out = peek(c.io.output.data(i))
      if (ens(i) == 1) {
        println("hw has " + out + " at port " + i + ", wanted " + fifo.head)
        expect(c.io.output.data(i), fifo.dequeue())
      }
    }
    step(1)
    (0 until ens.length).foreach{ i => poke(c.io.xBarR(i).en,0)}
  }

  // fill FIFO halfway
  var things_pushed = 0
  for (i <- 0 until c.depth/c.xBarWMux.values.head/2) {
    val ens = (0 until c.xBarWMux.values.head).map{i => rnd.nextInt(2)}
    val datas = (0 until c.xBarWMux.values.head).map{i => rnd.nextInt(5)}
    things_pushed = things_pushed + ens.reduce{_+_}
    enq(datas, ens)
  }

  // hold for a bit
  step(5)

  // pop FIFO halfway
  var things_popped = 0
  for (i <- 0 until c.depth/c.xBarRMux.values.head/2) {
    val ens = (0 until c.xBarRMux.values.head).map{i => rnd.nextInt(2)}
    things_popped = things_popped + ens.reduce{_+_}
    deq(if (things_popped > things_pushed) (0 until c.xBarRMux.values.head).map{_ => 0} else ens)
  }

  
}
