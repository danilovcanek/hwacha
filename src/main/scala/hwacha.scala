package hwacha

import Chisel._
import uncore._
import Constants._

case class HwachaConfiguration(vicache: rocket.ICacheConfig, dcache: rocket.DCacheConfig, nbanks: Int, nreg_per_bank: Int, ndtlb: Int, nptlb: Int)
{
  val nreg_total = nbanks * nreg_per_bank
  val vru = true
  val confprec = false

  // rocket pipeline latencies
  val dfma_stages = 4
  val sfma_stages = 3

  // pipeline latencies
  val int_stages = 2
  val imul_stages = 4
  val fma_stages = 3
  val fconv_stages = 3

  val delay_seq_exp = 2

  val ptr_incr_max =
    nbanks-1 +
    List(int_stages+2, imul_stages+3, fma_stages+4, fconv_stages+2).reduce(scala.math.max(_,_)) +
    delay_seq_exp +
    2 // buffer
  val ptr_incr_sz = log2Up(ptr_incr_max)

  val shift_buf_read = 3
  val shift_buf_write =
    List(imul_stages+3, fma_stages+4, fconv_stages+2).reduce(scala.math.max(_,_)) + 1

  val vcmdq = new {
    val ncmd = 19
    val nimm1 = 19
    val nimm2 = 17
    val ncnt = 8
  }

  val vmu = hwacha.vmu.HwachaVMUConfig()

  val nvvaq = 16
  val nvpaq = 16
  val nvpfvaq = 16
  val nvpfpaq = 16
  val nvldq = 128
  val nvsdq = 16
  val nvpasdq = 31
  val nvsreq = 128
  val nvlreq = nvldq
}

trait HwachaDecodeConstants
{
  val Y = Bool(true)
  val N = Bool(false)
  val X = Bits("b?", 1)

  val VRT_X = Bits("b?", 1)
  val VRT_I = Bits(0, 1)
  val VRT_F = Bits(1, 1)

  val VR_X   = Bits("b?", 1)
  val VR_RS1 = Bits(0, 1)
  val VR_RD  = Bits(1, 1)

  val VIMM_X    = Bits("b???",3)
  val VIMM_VLEN = Bits(0,3)
  val VIMM_RS1  = Bits(1,3)
  val VIMM_RS2  = Bits(2,3)
  val VIMM_ADDR = Bits(3,3)

  val RESP_X     = Bits("b???",3)
  val RESP_NVL   = Bits(0,3)
  val RESP_CAUSE = Bits(1,3)
  val RESP_AUX   = Bits(2,3)
  val RESP_CFG   = Bits(3,3)
  val RESP_VL    = Bits(4,3)

  val SZ_PREC = 2
  val PREC_DOUBLE = Bits("b00", SZ_PREC)
  val PREC_SINGLE = Bits("b01", SZ_PREC)
  val PREC_HALF   = Bits("b10", SZ_PREC)
}

object HwachaDecodeTable extends HwachaDecodeConstants
{
  import HwachaInstructions._
  import Commands._
                // * means special case decode code below                                  checkvl?
                //                                                                         | vcmd?
                //     inst_val                                                            | | vimm1?                  evac
                //     |  priv                                                             | | | vimm2?  resp?         | hold
                //     |  |  vmcd_val        reg_valid                                     | | | |       |             | |
                //     |  |  |  vcmd         |  rtype  reg1   reg2    imm1      imm2       | | | | vcnt? | resptype    | | kill
                //     |  |  |  |            |  |      |      |       |         |          | | | | |     | |           | | |
  val default =   List(N, N, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    N,RESP_X,     N,N,N)
  val table = Array( 
    // General instructions
    VSETCFG    -> List(Y, N, Y, CMD_VSETCFG, N, VRT_X, VR_X,  VR_X,   VIMM_VLEN,VIMM_X,    N,Y,Y,N,N,    N,RESP_X,     N,N,N), //* set maxvl register
    VSETVL     -> List(Y, N, Y, CMD_VSETVL,  N, VRT_X, VR_X,  VR_X,   VIMM_VLEN,VIMM_X,    N,Y,Y,N,N,    Y,RESP_NVL,   N,N,N), //* set vl register
    VGETCFG    -> List(Y, N, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    Y,RESP_CFG,   N,N,N),
    VGETVL     -> List(Y, N, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    Y,RESP_VL,    N,N,N),
    VF         -> List(Y, N, Y, CMD_VF,      N, VRT_X, VR_X,  VR_X,   VIMM_ADDR,VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VMVV       -> List(Y, N, Y, CMD_VMVV,    Y, VRT_I, VR_RD, VR_RS1, VIMM_X,   VIMM_X,    Y,Y,N,N,N,    N,RESP_X,     N,N,N),
    VMSV       -> List(Y, N, Y, CMD_VMSV,    Y, VRT_I, VR_RD, VR_RS1, VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VFMVV      -> List(Y, N, Y, CMD_VFMVV,   Y, VRT_F, VR_RD, VR_RS1, VIMM_X,   VIMM_X,    Y,Y,N,N,N,    N,RESP_X,     N,N,N),
    // Memory load/stores (x-registers)
    VLD        -> List(Y, N, Y, CMD_VLD,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VLW        -> List(Y, N, Y, CMD_VLW,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VLWU       -> List(Y, N, Y, CMD_VLWU,    Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VLH        -> List(Y, N, Y, CMD_VLH,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VLHU       -> List(Y, N, Y, CMD_VLHU,    Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VLB        -> List(Y, N, Y, CMD_VLB,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VLBU       -> List(Y, N, Y, CMD_VLBU,    Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VSD        -> List(Y, N, Y, CMD_VSD,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VSW        -> List(Y, N, Y, CMD_VSW,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VSH        -> List(Y, N, Y, CMD_VSH,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VSB        -> List(Y, N, Y, CMD_VSB,     Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    // Memory load/stores (fp-registers)
    VFLD       -> List(Y, N, Y, CMD_VFLD,    Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VFLW       -> List(Y, N, Y, CMD_VFLW,    Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VFSD       -> List(Y, N, Y, CMD_VFSD,    Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    VFSW       -> List(Y, N, Y, CMD_VFSW,    Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_X,    Y,Y,Y,N,N,    N,RESP_X,     N,N,N),
    // Memory strided load/stores (x-registers)
    VLSTD      -> List(Y, N, Y, CMD_VLSTD,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VLSTW      -> List(Y, N, Y, CMD_VLSTW,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VLSTWU     -> List(Y, N, Y, CMD_VLSTWU,  Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VLSTH      -> List(Y, N, Y, CMD_VLSTH,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VLSTHU     -> List(Y, N, Y, CMD_VLSTHU,  Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VLSTB      -> List(Y, N, Y, CMD_VLSTB,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VLSTBU     -> List(Y, N, Y, CMD_VLSTBU,  Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VSSTD      -> List(Y, N, Y, CMD_VSSTD,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VSSTW      -> List(Y, N, Y, CMD_VSSTW,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VSSTH      -> List(Y, N, Y, CMD_VSSTH,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VSSTB      -> List(Y, N, Y, CMD_VSSTB,   Y, VRT_I, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    // Memory strided load/stores (fp-registers)
    VFLSTD     -> List(Y, N, Y, CMD_VFLSTD,  Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VFLSTW     -> List(Y, N, Y, CMD_VFLSTW,  Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VFSSTD     -> List(Y, N, Y, CMD_VFSSTD,  Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    VFSSTW     -> List(Y, N, Y, CMD_VFSSTW,  Y, VRT_F, VR_RD, VR_RD,  VIMM_RS1, VIMM_RS2,  Y,Y,Y,Y,N,    N,RESP_X,     N,N,N),
    // Exception and save/restore instructions
    VXCPTCAUSE -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    Y,RESP_CAUSE, N,N,N),
    VXCPTAUX   -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    Y,RESP_AUX,   N,N,N),
    VXCPTSAVE  -> List(N, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    N,RESP_X,     N,N,N),
    VXCPTRESTORE->List(N, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    N,RESP_X,     N,N,N),
    VXCPTEVAC  -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    N,RESP_X,     Y,N,N), //* rs1 -> evac_addr
    VXCPTHOLD  -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    N,RESP_X,     N,Y,N),
    VXCPTKILL  -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,N,    N,RESP_X,     N,N,Y),
    VENQCMD    -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,Y,N,N,N,    N,RESP_X,     N,N,N),
    VENQIMM1   -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_RS1, VIMM_X,    N,N,Y,N,N,    N,RESP_X,     N,N,N),
    VENQIMM2   -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_RS1,  N,N,N,Y,N,    N,RESP_X,     N,N,N),
    VENQCNT    -> List(Y, Y, N, CMD_X,       N, VRT_X, VR_X,  VR_X,   VIMM_X,   VIMM_X,    N,N,N,N,Y,    N,RESP_X,     N,N,N)
  )
}

class Hwacha(hc: HwachaConfiguration, rc: rocket.RocketConfiguration) extends rocket.RoCC(rc)
{
  import HwachaDecodeTable._
  import Commands._
  
  implicit val conf = hc

  // D$ tag requirement for hwacha
  require(rc.dcacheReqTagBits >= log2Up(conf.nvldq))

  val icache = Module(new rocket.Frontend()(hc.vicache, rc.tl))
  val dtlb = Module(new rocket.TLB(hc.ndtlb))
  val ptlb = Module(new rocket.TLB(hc.nptlb))

  val irq = Module(new IRQ)
  val xcpt = Module(new XCPT)
  val vu = Module(new VU)
 
  // Cofiguration state
  val cfg_maxvl = Reg(init=UInt(32, log2Up(hc.nreg_total)+1))
  val cfg_vl    = Reg(init=UInt(0, log2Up(hc.nreg_total)+1))
  val cfg_regs  = Reg(init=Cat(UInt(32, 6), UInt(32, 6)))
  val cfg_xregs = cfg_regs(5,0)
  val cfg_fregs = cfg_regs(11,6)
  
  // Decode
  val raw_inst = io.cmd.bits.inst.toBits
  val inst_i_imm = raw_inst(31, 20)
  val logic = rocket.DecodeLogic(raw_inst, HwachaDecodeTable.default, HwachaDecodeTable.table)
  val cs = logic.map {
    case b if b.inputs.head.getClass == classOf[Bool] => b.toBool
    case u => u 
  }

  val resp_q = Module( new Queue(io.resp.bits, 2) )
  resp_q.io.deq <> io.resp

  val (inst_val: Bool) :: (inst_priv: Bool) :: (vcmd_valid: Bool) :: sel_vcmd :: (vr_valid: Bool) :: vr_type :: sel_vr1 :: sel_vr2 :: sel_vimm1 :: sel_vimm2 :: cs0 = cs
  val (check_vl: Bool) :: (emit_vcmd: Bool) :: (emit_vimm1: Bool) :: (emit_vimm2: Bool) :: (emit_cnt: Bool) :: cs1 = cs0
  val (emit_response: Bool) :: sel_resp :: (decl_evac: Bool):: (decl_hold: Bool) :: (decl_kill: Bool) :: Nil = cs1

  val irq_top = irq.io.vu.top.illegal_cfg || irq.io.vu.top.illegal_inst || irq.io.vu.top.priv_inst || irq.io.vu.top.illegal_regid
  val stall_hold = Reg(init=Bool(false))
  val stall = stall_hold || irq_top || xcpt.io.vu.prop.top.stall

  when (irq_top) { stall_hold := Bool(true) }
  when (xcpt.io.vu.prop.vu.flush_top) { stall_hold := Bool(false) }

  val cmd_valid = inst_val && io.cmd.valid && (!check_vl || cfg_vl != UInt(0))
  val resp_ready  = !emit_response || resp_q.io.enq.ready
  val vcmd_ready  = !emit_vcmd  || vu.io.vcmdq_user_ready
  val vimm1_ready = !emit_vimm1 || vu.io.vimm1q_user_ready
  val vimm2_ready = !emit_vimm2 || vu.io.vimm2q_user_ready
  val vcnt_ready  = !emit_cnt || vu.io.vcmdq.cnt.ready

  def construct_ready(exclude: Bool): Bool = {
    val all_readies = Array(!stall, resp_ready, vcmd_ready, vimm1_ready, vimm2_ready, vcnt_ready)
    all_readies.filter(_ != exclude).reduce(_&&_)
  }

  // Connect supporting Hwacha memory modules to external ports
  io.imem <> icache.io.mem
  io.iptw <> icache.io.cpu.ptw
  io.dptw <> dtlb.io.ptw
  io.pptw <> ptlb.io.ptw

  // Connect VU to I$
  icache.io.cpu <> vu.io.imem

  // Connect VU to D$
  io.mem <> vu.io.dmem

  // Connect VU to DTLB and PTLB
  vu.io.vtlb <> dtlb.io
  vu.io.vpftlb <> ptlb.io

  // Busy signal for fencing
  io.busy := vu.io.busy || cmd_valid

  // TODO: SETUP PREFETCH QUEUES

  // Setup interrupt 
  io.interrupt := irq.io.rocc.request

  val reg_prec = Reg(init = PREC_DOUBLE)
  val next_prec = Bits(width = SZ_PREC)

  val prec = (io.cmd.bits.rs1(13,12)).zext.toUInt
  if (conf.confprec) {
    next_prec := MuxLookup(prec, PREC_DOUBLE, Array(
      UInt(0,2) -> PREC_DOUBLE,
      UInt(1,2) -> PREC_SINGLE,
      UInt(2,2) -> PREC_HALF
    ))
  } else {
    next_prec := PREC_DOUBLE
  }

  // Logic to handle vector length calculation
  val nxpr = (io.cmd.bits.rs1( 5,0) + inst_i_imm( 5,0)).zext.toUInt
  val nfpr = (io.cmd.bits.rs1(11,6) + inst_i_imm(11,6)).zext.toUInt

  val packing = MuxLookup(next_prec, UInt(0), Array(
    PREC_DOUBLE -> UInt(0),
    PREC_SINGLE -> UInt(1),
    PREC_HALF   -> UInt(2)
  ))

  // vector length lookup
  val regs_used = (Mux(nxpr === UInt(0), UInt(0), nxpr - UInt(1)) << packing) + nfpr
  val vlen_width = log2Up(hc.nreg_per_bank + 1)
  val rom_allocation_units = (0 to 164).toArray.map(n => (UInt(n),
    UInt(if (n < 2) (hc.nreg_per_bank) else (hc.nreg_per_bank / n), width = vlen_width)
  ))

  val ut_per_bank = Lookup(regs_used, rom_allocation_units.last._2, rom_allocation_units) << packing
  val new_maxvl = ut_per_bank << UInt(3) // microthreads
  val xf_split = (new_maxvl >> UInt(3)) * (nxpr - UInt(1))

  val new_vl = Mux(io.cmd.bits.rs1 < cfg_maxvl, io.cmd.bits.rs1, cfg_maxvl)

  val vimm_vlen = Cat(UInt(0,18), next_prec, xf_split(7,0), UInt(8,4), SInt(-1,8), nfpr(5,0), nxpr(5,0), new_vl(SZ_VLEN-1,0))
  when (cmd_valid && vcmd_valid && construct_ready(null)) {
    switch (sel_vcmd) {
      is (CMD_VSETCFG) {
        cfg_maxvl := new_maxvl
        cfg_vl := UInt(0)
        cfg_regs := Cat(nfpr(5,0),nxpr(5,0))
        reg_prec := next_prec
      }
      is (CMD_VSETVL) {
        cfg_vl := new_vl
      }
    }
  }

  // Calculate the vf address
  val vf_immediate = Cat(raw_inst(31,25),raw_inst(11,7)).toSInt
  val vimm_addr = io.cmd.bits.rs1 + vf_immediate

  // Hookup ready port of cmd queue
  io.cmd.ready := construct_ready(null)

  // Hookup vcmdq.cmd
  val vr1 = Mux(sel_vr1===VR_RS1, raw_inst(19,15), raw_inst(11,7))
  val vr2 = Mux(sel_vr2===VR_RS1, raw_inst(19,15), raw_inst(11,7))
  val construct_vcmd = Cat(sel_vcmd, vr1, vr2)

  vu.io.vcmdq.cmd.valid := cmd_valid && emit_vcmd && construct_ready(vcmd_ready) 
  vu.io.vcmdq.cmd.bits :=
    new HwachaCommand().fromBits(Mux(vcmd_valid, construct_vcmd, io.cmd.bits.rs1))
  
  // Hookup vcmdq.imm1
  vu.io.vcmdq.imm1.valid := cmd_valid && emit_vimm1 && construct_ready(vimm1_ready) 
  vu.io.vcmdq.imm1.bits := MuxLookup(sel_vimm1, vimm_vlen, Array(
    VIMM_VLEN -> vimm_vlen,
    VIMM_RS1  -> io.cmd.bits.rs1,
    VIMM_RS2  -> io.cmd.bits.rs2,
    VIMM_ADDR -> vimm_addr
  ))

  // Hookup vcmdq.imm2
  vu.io.vcmdq.imm2.valid := cmd_valid && emit_vimm2 && construct_ready(vimm2_ready) 
  vu.io.vcmdq.imm2.bits := MuxLookup(sel_vimm2, vimm_vlen, Array(
    VIMM_VLEN -> vimm_vlen,
    VIMM_RS1  -> io.cmd.bits.rs1,
    VIMM_RS2  -> io.cmd.bits.rs2,
    VIMM_ADDR -> vimm_addr
  ))

  // Hookup vcmdq.cnt
  vu.io.vcmdq.cnt.valid := cmd_valid && emit_cnt && construct_ready(vcnt_ready)
  vu.io.vcmdq.cnt.bits := io.cmd.bits.rs1
  
  // Hookup resp queue
  resp_q.io.enq.valid := cmd_valid && emit_response && construct_ready(resp_ready)
  resp_q.io.enq.bits.data := MuxLookup(sel_resp, new_vl, Array(
    RESP_NVL   -> new_vl,
    RESP_CAUSE -> irq.io.rocc.cause,
    RESP_AUX   -> irq.io.rocc.aux,
    RESP_CFG   -> cfg_regs,
    RESP_VL    -> cfg_vl
  ))
  resp_q.io.enq.bits.rd := io.cmd.bits.inst.rd

  // Hookup some signal ports
  irq.io.vu <> vu.io.irq
  irq.io.rocc.clear := resp_q.io.enq.valid && sel_resp === RESP_CAUSE

  irq.io.vu.top.illegal_cfg := io.cmd.valid &&
    vcmd_valid && (sel_vcmd === CMD_VSETCFG) && (nxpr > UInt(32) || nfpr > UInt(32))
  irq.io.vu.top.illegal_inst := io.cmd.valid && !inst_val
  irq.io.vu.top.priv_inst := io.cmd.valid && inst_priv && !io.s
  irq.io.vu.top.illegal_regid := Bool(false)
/*  irq.io.vu.top.illegal_regid := io.cmd.valid && vr_valid &&
    Mux(vr_type===VRT_I, vr1 >= cfg_xregs || vr2 >= cfg_xregs,
                         vr1 >= cfg_fregs || vr2 >= cfg_fregs)
*/
  irq.io.vu.top.aux := MuxCase(
    raw_inst, Array(
      (irq.io.vu.top.illegal_cfg && nxpr > UInt(32)) -> UInt(0),
      (irq.io.vu.top.illegal_cfg && nfpr > UInt(32)) -> UInt(1)
    ))
  
  val reg_hold = Reg(init=Bool(false))
  when (cmd_valid && decl_hold && construct_ready(null)) { reg_hold := Bool(true) }
  when (reg_hold && !io.s) { reg_hold := Bool(false) }

  xcpt.io.rocc.exception := io.exception
  xcpt.io.rocc.evac := cmd_valid && decl_evac && construct_ready(null)
  xcpt.io.rocc.evac_addr := io.cmd.bits.rs1
  xcpt.io.rocc.hold := reg_hold
  xcpt.io.rocc.kill := cmd_valid && decl_kill && construct_ready(null)
  vu.io.xcpt <> xcpt.io.vu

  // TODO: hook this stuff up properly
  vu.io.vpfcmdq.cmd.valid := Bool(false)
  vu.io.vpfcmdq.imm1.valid := Bool(false)
  vu.io.vpfcmdq.imm2.valid := Bool(false)
  vu.io.vpfcmdq.cnt.valid := Bool(false)
}
