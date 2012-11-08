package hwacha

import Chisel._
import Node._
import Constants._

class io_vmu_store_data extends Bundle
{
  val vsdq_lane = new io_vsdq().flip
  val vsdq_evac = new io_vsdq().flip

  val vsdq = new io_vsdq()

  val vsdq_lane_dec = Bool(INPUT)

  val vsdq_do_enq = Bool(OUTPUT)
  val vsdq_do_deq = Bool(OUTPUT)
  val vpasdq_watermark = Bool(INPUT)
  val vsdq_watermark = Bool(INPUT)

  val evac_to_vmu = new io_evac_to_vmu().flip
}

class vuVMU_StoreData extends Component
{
  val io = new io_vmu_store_data()

  val vsdq_arb = (new Arbiter(2)){ Bits(width = SZ_DATA) }
  val vsdq = new Queue(ENTRIES_VSDQ)(Bits(width = 65))

  vsdq_arb.io.in(0) <> io.vsdq_lane
  vsdq_arb.io.in(1) <> io.vsdq_evac
  vsdq_arb.io.out.ready :=
    Mux(io.evac_to_vmu.evac_mode, vsdq.io.enq.ready,
        io.vsdq_watermark && io.vpasdq_watermark)

  vsdq.io.enq.valid := vsdq_arb.io.out.valid
  vsdq.io.enq.bits := vsdq_arb.io.out.bits

  io.vsdq <> vsdq.io.deq

  io.vsdq_do_enq :=
    Mux(io.evac_to_vmu.evac_mode, vsdq.io.enq.ready && io.vsdq_evac.valid,
        io.vsdq_lane_dec)
  io.vsdq_do_deq := io.vsdq.ready && vsdq.io.deq.valid
}
