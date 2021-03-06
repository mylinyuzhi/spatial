package spatial.codegen.chiselgen

import argon._
import argon.codegen.Codegen
import spatial.lang._
import spatial.node._
import spatial.internal.{spatialConfig => cfg}
import spatial.data._
import spatial.util._

trait ChiselGenStream extends ChiselGenCommon {
  var streamIns: List[Sym[Reg[_]]] = List()
  var streamOuts: List[Sym[Reg[_]]] = List()

  override protected def gen(lhs: Sym[_], rhs: Op[_]): Unit = rhs match {
    case StreamInNew(bus) =>
      emitGlobalWireMap(src"${lhs}_ready_options", src"Wire(Vec(${readersOf(lhs).toList.length}, Bool()))", forceful = true)
      emitGlobalWireMap(src"${lhs}_ready", "Wire(Bool())", forceful = true)
      emitGlobalWire(src"${swap(lhs, Ready)} := ${swap(lhs, ReadyOptions)}.reduce{_|_}", forceful = true)
      emitGlobalWireMap(src"""${lhs}_now_valid""","""Wire(Bool())""", forceful = true)
      emitGlobalWireMap(src"${lhs}_valid", "Wire(Bool())", forceful = true)
      emitGlobalWire(src"val ${lhs} = Wire(${readersOf(lhs).toList.head.tp})", forceful = true)

    // case op@StreamInBankedRead(strm, ens) =>
    //   open(src"val $lhs = {")
    //   ens.zipWithIndex.foreach{case (en,i) =>
    //     emit(src"val a$i = if (${and(en)} && $strm.nonEmpty) $strm.dequeue() else ${invalid(op.A)}")
    //   }
    //   emit(src"Array[${op.A}](" + ens.indices.map{i => src"a$i"}.mkString(", ") + ")")
    //   close("}")

    // case StreamOutBankedWrite(strm, data, ens) =>
    //   open(src"val $lhs = {")
    //   ens.zipWithIndex.foreach{case (en,i) =>
    //     emit(src"if (${and(en)}) $strm.enqueue(${data(i)})")
    //   }
    //   close("}")

    case StreamOutNew(bus) =>
      emitGlobalWireMap(src"${lhs}_valid_options", src"Wire(Vec(${writersOf(lhs).toList.length}, Bool()))", forceful = true)
      emitGlobalWireMap(src"${lhs}_valid_stops", src"Wire(Vec(${writersOf(lhs).toList.length}, Bool()))", forceful = true)
      emitGlobalWireMap(src"${lhs}_valid", "Wire(Bool())", forceful = true)
      emitGlobalWireMap(src"${lhs}_stop", "Wire(Bool())", forceful = true)
      emitGlobalModuleMap(src"${lhs}_valid_srff", "Module(new SRFF())", forceful = true)
      emitGlobalModule(src"${swap(src"${lhs}_valid_srff", Blank)}.io.input.set := ${swap(lhs, ValidOptions)}.reduce{_|_}", forceful = true)
      emitGlobalModule(src"${swap(src"${lhs}_valid_srff", Blank)}.io.input.reset := ${swap(src"${lhs}_valid_stops", Blank)}.reduce{_|_}", forceful = true)
      emitGlobalModule(src"${swap(src"${lhs}_valid_srff", Blank)}.io.input.asyn_reset := ${swap(src"${lhs}_valid_stops", Blank)}.reduce{_|_} | accelReset", forceful = true)
      emitGlobalModule(src"${swap(lhs, Valid)} := ${swap(src"${lhs}_valid_srff", Blank)}.io.output.data | ${swap(lhs, ValidOptions)}.reduce{_|_}", forceful = true)
      val ens = writersOf(lhs).toList.head match {case Op(StreamOutBankedWrite(_, _, ens)) => ens.toList.length ; case _ => 0}
	    emitGlobalWireMap(src"${lhs}_data_options", src"Wire(Vec(${ens*writersOf(lhs).toList.length}, ${lhs.tp.typeArgs.head}))")
	    emitGlobalWire(src"""val ${lhs} = Vec((0 until ${ens}).map{i => val ${lhs}_slice_options = (0 until ${writersOf(lhs).toList.length}).map{j => ${swap(lhs, DataOptions)}(i*${writersOf(lhs).toList.length}+j)}; Mux1H(${swap(lhs, ValidOptions)}, ${lhs}_slice_options)}.toList)""")
      emitGlobalWireMap(src"${lhs}_ready", "Wire(Bool())", forceful = true)

//     case StreamRead(stream, en) =>
//       val isAck = stream match {
//         case Def(StreamInNew(bus)) => bus match {
//           case BurstAckBus => true
//           case ScatterAckBus => true
//           case _ => false
//         }
//         case _ => false
//       }
//       val parent = parentOf(lhs).get
//       emit(src"""val ${lhs}_rId = getStreamInLane("$stream")""")
//       emit(src"""${swap(stream, ReadyOptions)}(${lhs}_rId) := ${en} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}) // Do not delay ready because datapath includes a delayed _valid already """)

//       // emit(src"""${swap(stream, ReadyOptions)}(${lhs}_rId) := ${en} & (${swap(parent, Done)} & ~${swap(parent, Inhibitor)}) // Do not delay ready because datapath includes a delayed _valid already """)
// //      emit(src"""${swap(stream, ReadyOptions)}(${lhs}_rId) := ${en} & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}) // Do not delay ready because datapath includes a delayed _valid already """)
//       if (!isAck) {
//         stream match {
//           case Def(StreamInNew(bus)) => bus match {
//             case VideoCamera => 
//               emit(src"""val $lhs = io.stream_in_data""")  // Ignores enable for now
//             case SliderSwitch => 
//               emit(src"""val $lhs = io.switch_stream_in_data""")
//             case GPInput1 => 
//               emit(src"""val $lhs = io.gpi1_streamin_readdata""")
//             case GPInput2 => 
//               emit(src"""val $lhs = io.gpi2_streamin_readdata""")
//             case BurstDataBus() => 
//               emit(src"""val $lhs = (0 until 1).map{ i => ${stream}(i) }""")

//             case _ =>
//               val id = argMapping(stream)._1
//               Predef.assert(id != -1, s"Stream ${quote(stream)} not present in streamIns")
//               emit(src"""val ${quote(lhs)} = io.genericStreams.ins($id).bits.data """)  // Ignores enable for now
//           }
//         }
//       } else {
//         emit(src"""// read is of burstAck on $stream""")
//       }

    case StreamOutBankedWrite(stream, data, ens) =>
      val parent = lhs.parent.s.get
      emit(src"""val ${lhs}_wId = getStreamOutLane("$stream")""")
      val muxPort = portsOf(lhs).values.head.muxPort
      val en = if (ens.isEmpty) "true.B" else src"${ens.flatten.toList.map(quote).mkString("&")}"
      emit(src"""${swap(stream, ValidOptions)}(${muxPort}) := ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", src"${symDelay(lhs)}.toInt", true)} & $en & ~${parent}_sm.io.ctrDone """)
      emit(src"""${swap(src"${stream}_valid_stops", Blank)}(${muxPort}) := ${swap(parent, Done)} | ~${parent}_sm.io.ctrDone // Should be delayed by body latency + ready-off bubbles""")
      emit(src"""${swap(stream, DataOptions)}(${muxPort}) := $data""")


    case StreamInBankedRead(strm, ens) =>
      val parent = lhs.parent.s.get
      val muxPort = portsOf(lhs).values.head.muxPort
      val en = if (ens.isEmpty) "true.B" else src"${ens.flatten.toList.map(quote).mkString("&")}"
      emitGlobalWireMap(src"$lhs", src"Wire(${lhs.tp})")
      emit(src"""${swap(strm, ReadyOptions)}(${muxPort}) := $en & (${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}) // Do not delay ready because datapath includes a delayed _valid already """)
      emit(src"""(0 until ${ens.length}).map{ i => ${lhs}(i) := ${strm}(i) }""")


//     case ParStreamWrite(stream, data, ens) =>
//       val par = ens.length
//       val parent = parentOf(lhs).get
//       val datacsv = data.map{d => src"${d}"}.mkString(",")
//       val en = ens.map(quote).mkString("&")

//       emit(src"""val ${lhs}_wId = getStreamOutLane("$stream")*-*${ens.length}""")
//       emit(src"""${swap(stream, ValidOptions)}(${lhs}_wId) := $en & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", src"${symDelay(lhs)}.toInt", true)} & ~${swap(parent, Done)} /*mask off double-enq for sram loads*/""")
//       emit(src"""${swap(src"${stream}_valid_stops", Blank)}(${lhs}_wId) := ${swap(parent, Done)} // Should be delayed by body latency + ready-off bubbles""")
//       (0 until ens.length).map{ i => emit(src"""${swap(stream, DataOptions)}(${lhs}_wId + ${i}) := ${data(i)}""")}
//       // emit(src"""${stream} := Vec(List(${datacsv}))""")

//       stream match {
//         case Def(StreamOutNew(bus)) => bus match {
//           case VGA => 
//             emitGlobalWire(src"""// EMITTING VGA GLOBAL""")
//             // emitGlobalWire(src"""val ${stream} = Wire(UInt(16.W))""")
//             // emitGlobalWire(src"""val converted_data = Wire(UInt(16.W))""")
//             emitGlobalWireMap(src"""stream_out_startofpacket""", """Wire(Bool())""")
//             emitGlobalWireMap(src"""stream_out_endofpacket""", """Wire(Bool())""")
//             emit(src"""stream_out_startofpacket := Utils.risingEdge(${swap(parent, DatapathEn)})""")
//             emit(src"""stream_out_endofpacket := ${swap(parent, Done)}""")
//             emit(src"""// emiiting data for stream ${stream}""")
//             // emit(src"""${stream} := ${data.head}""")
//             // emit(src"""converted_data := ${stream}""")
//             // emit(src"""${stream}_valid := ${ens.mkString("&")} & ShiftRegister(${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}, ${symDelay(lhs)}.toInt)""")
//           case LEDR =>
//             // emitGlobalWire(src"""val ${stream} = Wire(UInt(32.W))""")
//       //      emitGlobalWire(src"""val converted_data = Wire(UInt(32.W))""")
//             // emit(src"""${stream} := $data""")
//             // emit(src"""io.led_stream_out_data := ${stream}""")
//           case _ =>
//             // val datacsv = data.map{d => src"${d}"}.mkString(",")
//             // val en = ens.map(quote).mkString("&")
//             // emit(src"${stream} := Vec(List(${datacsv}))")
//             // emit(src"${stream}_valid := $en & ${DL(src"${swap(parent, DatapathEn)} & ~${swap(parent, Inhibitor)}", src"${symDelay(lhs)}.toInt", true)} & ~${parent}_done /*mask off double-enq for sram loads*/")
//         }
//       }

    case _ => super.gen(lhs, rhs)
  }

  override def emitFooter(): Unit = {
  	enterAccel()
    val insList = List.fill(streamIns.length){ "StreamParInfo(32, 1)" }.mkString(",")
    val outsList = List.fill(streamOuts.length){ "StreamParInfo(32, 1)" }.mkString(",")

    inGenn(out, "IOModule", ext) {
      emitt(src"// Non-memory Streams")
      emitt(s"""val io_streamInsInfo = List(${insList})""")
      emitt(s"""val io_streamOutsInfo = List(${outsList})""")
    }

    inGen(out, "Instantiator.scala") {
      emit(src"// Non-memory Streams")
      emit(s"""val streamInsInfo = List(${insList})""")
      emit(s"""val streamOutsInfo = List(${outsList})""")
    }
    exitAccel()
    super.emitFooter()
  }
}
