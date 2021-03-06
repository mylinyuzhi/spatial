package spatial.tests.feature.banking


import spatial.dsl._


@test class LinearWriteRandomRead extends SpatialTest {
  override def runtimeArgs: Args = NoArgs


  def main(args: Array[String]): Unit = {
    Accel {
      val sram = SRAM[Int](16)
      val addr = SRAM[Int](16)
      val out1 = ArgOut[Int]
      val out2 = ArgOut[Int]
      Foreach(16 by 1){i =>
        Foreach(16 by 1 par 2){j =>
          sram(j) = i*j
          addr(j) = 16 - j
        }
        val sum = Reduce(0)(16 par 5){j => sram(addr(j)) }{_+_}
        out1 := sum
      }
    }
  }
}
