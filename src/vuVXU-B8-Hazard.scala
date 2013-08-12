package hwacha

import Chisel._
import Node._
import Constants._

class io_vxu_hazard_to_issue_tvec extends Bundle
{
  val pending_memop = Bool()
}

class io_vxu_hazard_to_issue extends Bundle
{
  val tvec = new io_vxu_hazard_to_issue_tvec()
}

class io_vxu_hazard extends Bundle
{
  val hazard_to_issue = new io_vxu_hazard_to_issue().asOutput
  val issue_to_hazard = new io_vxu_issue_to_hazard().asInput
  val seq_to_hazard = new io_vxu_seq_to_hazard().asInput
  val expand_to_hazard = new io_vxu_expand_to_hazard().asInput
  val lane_to_hazard = new io_lane_to_hazard().asInput

  val tvec_valid = new io_vxu_issue_fire().asInput
  val tvec_ready = Bool(OUTPUT)
  val tvec_dhazard = new io_vxu_issue_reg().asInput
  val tvec_shazard = new io_vxu_issue_fu().asInput
  val tvec_bhazard = new io_vxu_issue_op().asInput
  val tvec_fn = new io_vxu_issue_fn().asInput
  val tvec_regid_imm = new io_vxu_issue_regid_imm().asInput

  val vt_valid = new io_vxu_issue_fire().asInput
  val vt_ready = Bool(OUTPUT)
  val vt_dhazard = new io_vxu_issue_reg().asInput
  val vt_shazard = new io_vxu_issue_fu().asInput
  val vt_bhazard = new io_vxu_issue_op().asInput
  val vt_fn = new io_vxu_issue_fn().asInput
  val vt_regid_imm = new io_vxu_issue_regid_imm().asInput

  val fire = new io_vxu_issue_fire().asInput
  val fire_fn = new io_vxu_issue_fn().asInput
  val fire_regid_imm = new io_vxu_issue_regid_imm().asInput
}

class vuVXU_Banked8_Hazard(resetSignal: Bool = null) extends Module(reset = resetSignal)
{
  val io = new io_vxu_hazard()

  val reg_ptr = RegReset(UInt(0,SZ_LGBANK))

  val next_ptr1_add = reg_ptr + UInt(1, SZ_LGBANK1)
  val next_ptr2_add = reg_ptr + UInt(2, SZ_LGBANK1)
  val next_ptr3_add = reg_ptr + UInt(3, SZ_LGBANK1)
  val next_ptr4_add = reg_ptr + UInt(4, SZ_LGBANK1)

  val next_ptr1_add_bcnt = next_ptr1_add - io.issue_to_hazard.bcnt
  val next_ptr2_add_bcnt = next_ptr2_add - io.issue_to_hazard.bcnt
  val next_ptr3_add_bcnt = next_ptr3_add - io.issue_to_hazard.bcnt
  val next_ptr4_add_bcnt = next_ptr4_add - io.issue_to_hazard.bcnt

  val next_ptr4_add_bcnt2 = next_ptr4_add_bcnt - io.issue_to_hazard.bcnt

  val next_ptr1 = Mux(
    next_ptr1_add < io.issue_to_hazard.bcnt, next_ptr1_add(SZ_LGBANK-1,0),
    next_ptr1_add_bcnt(SZ_LGBANK-1,0))

  val next_ptr2 = Mux(
    next_ptr2_add < io.issue_to_hazard.bcnt, next_ptr2_add(SZ_LGBANK-1,0),
    next_ptr2_add_bcnt(SZ_LGBANK-1,0))

  val next_ptr3 = Mux(
    next_ptr3_add < io.issue_to_hazard.bcnt, next_ptr3_add(SZ_LGBANK-1,0),
    next_ptr3_add_bcnt(SZ_LGBANK-1,0))

  val next_ptr4 = Mux(
    next_ptr4_add < io.issue_to_hazard.bcnt, next_ptr4_add(SZ_LGBANK-1,0), Mux(
    next_ptr4_add_bcnt < io.issue_to_hazard.bcnt, next_ptr4_add_bcnt(SZ_LGBANK-1,0),
    next_ptr4_add_bcnt2(SZ_LGBANK-1,0)))

  reg_ptr := next_ptr1

  val array_rport_val = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_rport_vau0 = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_rport_vau1 = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_rport_vau2 = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_rport_vsu = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_rport_vgu = Vec.fill(SZ_BANK){RegReset(Bool(false))}

  val next_rport_val = Vec.fill(SZ_BANK){Bool()}
  val next_rport_vau0 = Vec.fill(SZ_BANK){Bool()}
  val next_rport_vau1 = Vec.fill(SZ_BANK){Bool()}
  val next_rport_vau2 = Vec.fill(SZ_BANK){Bool()}
  val next_rport_vsu = Vec.fill(SZ_BANK){Bool()}
  val next_rport_vgu = Vec.fill(SZ_BANK){Bool()}

  for (i <- 0 until SZ_BANK)
  {
    array_rport_val(i) := next_rport_val(i)
    array_rport_vau0(i) := next_rport_vau0(i)
    array_rport_vau1(i) := next_rport_vau1(i)
    array_rport_vau2(i) := next_rport_vau2(i)
    array_rport_vsu(i) := next_rport_vsu(i)
    array_rport_vgu(i) := next_rport_vgu(i)
  }

  for (i <- 0 until SZ_BANK)
  {
    next_rport_val(i) := array_rport_val(i)
    next_rport_vau0(i) := array_rport_vau0(i)
    next_rport_vau1(i) := array_rport_vau1(i)
    next_rport_vau2(i) := array_rport_vau2(i)
    next_rport_vsu(i) := array_rport_vsu(i)
    next_rport_vgu(i) := array_rport_vgu(i)
  }
  
  when (io.fire.viu)
  {
    next_rport_val(next_ptr2) := Bool(true)

    when (io.fire_fn.viu(RG_VIU_T) === Cat(ML,MR))
    {
      next_rport_val(next_ptr3) := Bool(true)
    }
  }
  when (io.fire.vau0)
  {
    next_rport_val(next_ptr2) := Bool(true)
    next_rport_vau0(next_ptr2) := Bool(true)

    next_rport_val(next_ptr3) := Bool(true)
    next_rport_vau0(next_ptr3) := Bool(true)
  }
  when (io.fire.vau1)
  {
    next_rport_val(next_ptr2) := Bool(true)
    next_rport_vau1(next_ptr2) := Bool(true)

    next_rport_val(next_ptr3) := Bool(true)
    next_rport_vau1(next_ptr3) := Bool(true)

    when (FN_VAU1_FMA(io.fire_fn.vau1))
    {
      next_rport_val(next_ptr4) := Bool(true)
      next_rport_vau1(next_ptr4) := Bool(true)
    }
  }
  when (io.fire.vau2)
  {
    next_rport_val(next_ptr2) := Bool(true)
    next_rport_vau2(next_ptr2) := Bool(true)
  }
  when (io.fire.amo || io.fire.utst)
  {
    next_rport_val(next_ptr2) := Bool(true)
    next_rport_vgu(next_ptr2) := Bool(true)

    next_rport_val(next_ptr3) := Bool(true)
    next_rport_vsu(next_ptr3) := Bool(true)
  }
  when (io.fire.utld)
  {
    next_rport_val(next_ptr2) := Bool(true)
    next_rport_vgu(next_ptr2) := Bool(true)
  }
  when (io.fire.vld)
  {
    next_rport_val(next_ptr2) := Bool(true)
    next_rport_vgu(next_ptr2) := Bool(true)
  }
  when (io.fire.vst)
  {
    next_rport_val(next_ptr2) := Bool(true)
    next_rport_vgu(next_ptr2) := Bool(true)
    
    next_rport_val(next_ptr3) := Bool(true)
    next_rport_vsu(next_ptr3) := Bool(true)
  }

  when (io.lane_to_hazard.rlast)
  {
    next_rport_val(reg_ptr) := Bool(false)
    next_rport_vau0(reg_ptr) := Bool(false)
    next_rport_vau1(reg_ptr) := Bool(false)
    next_rport_vau2(reg_ptr) := Bool(false)
    next_rport_vsu(reg_ptr) := Bool(false)
    next_rport_vgu(reg_ptr) := Bool(false)
  }

  // I had to change this structure to the following in order to cut the
  // critical path.  I'm precomputing all possible write pointers and then
  // muxing them in later.

  // this delay comes from seq/expand

  val DELAY = 2

  // tvec wptr calculation

  val tvec_viu_incr = UInt(INT_STAGES,SZ_LGBANK+1) + UInt(1, SZ_LGBANK) + UInt(DELAY, SZ_LGBANK)

  val tvec_viuwptr = Module(new vuVXU_Pointer)

  tvec_viuwptr.io.ptr := reg_ptr
  tvec_viuwptr.io.incr := tvec_viu_incr
  tvec_viuwptr.io.bcnt <> io.issue_to_hazard.bcnt

  val tvec_viu_wptr1 = tvec_viuwptr.io.nptr

  val tvec_viu_wptr1_add = tvec_viu_wptr1 + UInt(1, SZ_LGBANK1)
  val tvec_viu_wptr1_add_sub = tvec_viu_wptr1_add - io.issue_to_hazard.bcnt

  val tvec_viu_wptr2 = Mux(
    tvec_viu_wptr1_add < io.issue_to_hazard.bcnt, tvec_viu_wptr1_add(SZ_LGBANK-1,0),
    tvec_viu_wptr1_add_sub(SZ_LGBANK-1,0))

  val tvec_viu_wptr = MuxCase(
    Bits(0,SZ_LGBANK), Array(
      (io.tvec_valid.viu && io.tvec_fn.viu(RG_VIU_T) === Cat(ML,MR)) -> tvec_viu_wptr2,
      io.tvec_valid.viu -> tvec_viu_wptr1
    ))

  // vt wptr calculation

  val vt_vbr_incr = UInt(INT_STAGES,SZ_LGBANK+1) + UInt(2, SZ_LGBANK) + UInt(DELAY, SZ_LGBANK)
  val vt_viu_incr = UInt(INT_STAGES,SZ_LGBANK+1) + UInt(1, SZ_LGBANK) + UInt(DELAY, SZ_LGBANK)
  val vt_vau0_incr = UInt(IMUL_STAGES,SZ_LGBANK+1) + UInt(2, SZ_LGBANK) + UInt(DELAY, SZ_LGBANK)
  val vt_vau1_incr = UInt(FMA_STAGES,SZ_LGBANK+1) + UInt(2, SZ_LGBANK) + UInt(DELAY, SZ_LGBANK)
  val vt_vau2_incr = UInt(FCONV_STAGES,SZ_LGBANK+1) + UInt(1, SZ_LGBANK) + UInt(DELAY, SZ_LGBANK)

  val vt_vbrwptr = Module(new vuVXU_Pointer)
  val vt_viuwptr = Module(new vuVXU_Pointer)
  val vt_vau0wptr = Module(new vuVXU_Pointer)
  val vt_vau1wptr = Module(new vuVXU_Pointer)
  val vt_vau2wptr = Module(new vuVXU_Pointer)

  vt_vbrwptr.io.ptr := reg_ptr
  vt_vbrwptr.io.incr := vt_vbr_incr
  vt_vbrwptr.io.bcnt <> io.issue_to_hazard.bcnt

  vt_viuwptr.io.ptr := reg_ptr
  vt_viuwptr.io.incr := vt_viu_incr
  vt_viuwptr.io.bcnt <> io.issue_to_hazard.bcnt

  vt_vau0wptr.io.ptr := reg_ptr
  vt_vau0wptr.io.incr := vt_vau0_incr
  vt_vau0wptr.io.bcnt <> io.issue_to_hazard.bcnt

  vt_vau1wptr.io.ptr := reg_ptr
  vt_vau1wptr.io.incr := vt_vau1_incr
  vt_vau1wptr.io.bcnt <> io.issue_to_hazard.bcnt

  vt_vau2wptr.io.ptr := reg_ptr
  vt_vau2wptr.io.incr := vt_vau2_incr
  vt_vau2wptr.io.bcnt <> io.issue_to_hazard.bcnt

  val vt_vbr_wptr = vt_vbrwptr.io.nptr
  val vt_viu_wptr1 = vt_viuwptr.io.nptr
  val vt_vau0_wptr = vt_vau0wptr.io.nptr
  val vt_vau1_wptr2 = vt_vau1wptr.io.nptr
  val vt_vau2_wptr = vt_vau2wptr.io.nptr

  val vt_viu_wptr1_add = vt_viu_wptr1 + UInt(1, SZ_LGBANK1)
  val vt_viu_wptr1_add_sub = vt_viu_wptr1_add - io.issue_to_hazard.bcnt

  val vt_vau1_wptr2_add = vt_vau1_wptr2 + UInt(1, SZ_LGBANK1)
  val vt_vau1_wptr2_add_sub = vt_vau1_wptr2_add - io.issue_to_hazard.bcnt

  val vt_viu_wptr2 = Mux(
    vt_viu_wptr1_add < io.issue_to_hazard.bcnt, vt_viu_wptr1_add(SZ_LGBANK-1,0),
    vt_viu_wptr1_add_sub(SZ_LGBANK-1,0))

  val vt_vau1_wptr3 = Mux(
    vt_vau1_wptr2_add < io.issue_to_hazard.bcnt, vt_vau1_wptr2_add(SZ_LGBANK-1,0),
    vt_vau1_wptr2_add_sub(SZ_LGBANK-1,0))

  val vt_viu_wptr = MuxCase(
    Bits(0,SZ_LGBANK), Array(
      (io.vt_valid.viu && io.vt_fn.viu(RG_VIU_T) === Cat(ML,MR)) -> vt_viu_wptr2,
      io.vt_valid.viu -> vt_viu_wptr1
    ))

  val vt_vau1_wptr = MuxCase(
    Bits(0,SZ_LGBANK), Array(
      (io.vt_valid.vau1 && FN_VAU1_FMA(io.vt_fn.vau1)) -> vt_vau1_wptr3,
      io.vt_valid.vau1 -> vt_vau1_wptr2
    ))

  val vt_wptr = MuxCase(
    Bits(0,SZ_LGBANK), Array(
      io.vt_valid.viu -> vt_viu_wptr,
      io.vt_valid.vau0 -> vt_vau0_wptr,
      io.vt_valid.vau1 -> vt_vau1_wptr,
      io.vt_valid.vau2 -> vt_vau2_wptr
    ))

  // for the fire port
  // we can look at the issue port because we're firing at the same cycle

  val vbr_wptr = vt_vbr_wptr
  val viu_wptr = Mux(io.tvec_valid.viu, tvec_viu_wptr, vt_viu_wptr)
  val vau0_wptr = vt_vau0_wptr
  val vau1_wptr = vt_vau1_wptr
  val vau2_wptr = vt_vau2_wptr

  val array_wmask_val = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wmask_head = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  
  val array_wport_val = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wport_head = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wport_vau0 = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wport_vau1 = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wport_vau2 = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wport_vlu = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wport_vd = Vec.fill(SZ_BANK){Reg(Bits(width = SZ_BREGLEN))}
  val array_wport_vd_base = Vec.fill(SZ_BANK){Reg(Bits(width = SZ_BREGLEN))}
  val array_wport_stride = Vec.fill(SZ_BANK){Reg(Bits(width = SZ_REGLEN))}
  val array_wport_tvec = Vec.fill(SZ_BANK){RegReset(Bool(false))}
  val array_wport_tag = Vec.fill(SZ_BANK){Reg(Bits(width = SZ_PVFB_TAG))}

  val next_wmask_val = Vec.fill(SZ_BANK){Bool()}
  val next_wmask_head = Vec.fill(SZ_BANK){Bool()}

  val next_wport_val = Vec.fill(SZ_BANK){Bool()}
  val next_wport_head = Vec.fill(SZ_BANK){Bool()}
  val next_wport_vau0 = Vec.fill(SZ_BANK){Bool()}
  val next_wport_vau1 = Vec.fill(SZ_BANK){Bool()}
  val next_wport_vau2 = Vec.fill(SZ_BANK){Bool()}
  val next_wport_vlu = Vec.fill(SZ_BANK){Bool()}
  val next_wport_vd = Vec.fill(SZ_BANK){Bits(width = SZ_BREGLEN)}
  val next_wport_vd_base = Vec.fill(SZ_BANK){Bits(width = SZ_BREGLEN)}
  val next_wport_stride = Vec.fill(SZ_BANK){Bits(width = SZ_REGLEN)}
  val next_wport_tvec = Vec.fill(SZ_BANK){Bool()}
  val next_wport_tag = Vec.fill(SZ_BANK){Bits(width = SZ_PVFB_TAG)}

  for (i <- 0 until SZ_BANK)
  {
    array_wmask_val(i) := next_wmask_val(i)
    array_wmask_head(i) := next_wmask_head(i)

    array_wport_val(i) := next_wport_val(i)
    array_wport_head(i) := next_wport_head(i)
    array_wport_vau0(i) := next_wport_vau0(i)
    array_wport_vau1(i) := next_wport_vau1(i)
    array_wport_vau2(i) := next_wport_vau2(i)
    array_wport_vlu(i) := next_wport_vlu(i)
    array_wport_vd(i) := next_wport_vd(i)
    array_wport_vd_base(i) := next_wport_vd_base(i)
    array_wport_stride(i) := next_wport_stride(i)
    array_wport_tvec(i) := next_wport_tvec(i)
    array_wport_tag(i) := next_wport_tag(i)
  }

  for (i <- 0 until SZ_BANK)
  {
    next_wmask_val(i) := array_wmask_val(i)
    next_wmask_head(i) := array_wmask_head(i)
    
    next_wport_val(i) := array_wport_val(i)
    next_wport_head(i) := array_wport_head(i)
    next_wport_vau0(i) := array_wport_vau0(i)
    next_wport_vau1(i) := array_wport_vau1(i)
    next_wport_vau2(i) := array_wport_vau2(i)
    next_wport_vlu(i) := array_wport_vlu(i)
    next_wport_vd(i) := array_wport_vd(i)
    next_wport_vd_base(i) := array_wport_vd_base(i)
    next_wport_stride(i) := array_wport_stride(i)
    next_wport_tvec(i) := array_wport_tvec(i)
    next_wport_tag(i) := array_wport_tag(i)
  }

  when (io.fire.viu)
  {
    when (isVIUBranch(io.fire_fn.viu(RG_VIU_FN)))
    {
      next_wmask_val(vbr_wptr) := Bool(true)
      next_wmask_head(vbr_wptr) := Bool(true)
    }
    .otherwise 
    {
      next_wport_val(viu_wptr) := Bool(true)
      next_wport_head(viu_wptr) := Bool(true)
      next_wport_vd(viu_wptr) := io.fire_regid_imm.vd
      next_wport_vd_base(viu_wptr) := io.fire_regid_imm.vd_base
      next_wport_stride(viu_wptr) := io.issue_to_hazard.stride
      next_wport_tvec(viu_wptr) := io.fire_regid_imm.tvec
      next_wport_tag(viu_wptr) := io.fire_regid_imm.pvfb_tag
    }
  }
  when (io.fire.vau0)
  {
    next_wport_val(vau0_wptr) := Bool(true)
    next_wport_head(vau0_wptr) := Bool(true)
    next_wport_vau0(vau0_wptr) := Bool(true)
    next_wport_vd(vau0_wptr) := io.fire_regid_imm.vd
    next_wport_vd_base(vau0_wptr) := io.fire_regid_imm.vd_base
    next_wport_stride(vau0_wptr) := io.issue_to_hazard.stride
    next_wport_tvec(vau0_wptr) := io.fire_regid_imm.tvec
    next_wport_tag(vau0_wptr) := io.fire_regid_imm.pvfb_tag
  }
  when (io.fire.vau1)
  {
    next_wport_val(vau1_wptr) := Bool(true)
    next_wport_head(vau1_wptr) := Bool(true)
    next_wport_vau1(vau1_wptr) := Bool(true)
    next_wport_vd(vau1_wptr) := io.fire_regid_imm.vd
    next_wport_vd_base(vau1_wptr) := io.fire_regid_imm.vd_base
    next_wport_stride(vau1_wptr) := io.issue_to_hazard.stride
    next_wport_tvec(vau1_wptr) := io.fire_regid_imm.tvec
    next_wport_tag(vau1_wptr) := io.fire_regid_imm.pvfb_tag
  }
  when (io.fire.vau2)
  {
    next_wport_val(vau2_wptr) := Bool(true)
    next_wport_head(vau2_wptr) := Bool(true)
    next_wport_vau2(vau2_wptr) := Bool(true)
    next_wport_vd(vau2_wptr) := io.fire_regid_imm.vd
    next_wport_vd_base(vau2_wptr) := io.fire_regid_imm.vd_base
    next_wport_stride(vau2_wptr) := io.issue_to_hazard.stride
    next_wport_tvec(vau2_wptr) := io.fire_regid_imm.tvec
    next_wport_tag(vau2_wptr) := io.fire_regid_imm.pvfb_tag
  }
  when (io.fire.amo)
  {
    next_wport_val(next_ptr4) := Bool(true)
    next_wport_head(next_ptr4) := Bool(true)
    next_wport_vlu(next_ptr4) := Bool(true)
    next_wport_vd(next_ptr4) := io.fire_regid_imm.vd
    next_wport_vd_base(next_ptr4) := io.fire_regid_imm.vd_base
    next_wport_stride(next_ptr4) := io.issue_to_hazard.stride
    next_wport_tvec(next_ptr4) := io.fire_regid_imm.tvec
    next_wport_tag(next_ptr4) := io.fire_regid_imm.pvfb_tag
  }
  when (io.fire.utld)
  {
    next_wport_val(next_ptr3) := Bool(true)
    next_wport_head(next_ptr3) := Bool(true)
    next_wport_vlu(next_ptr3) := Bool(true)
    next_wport_vd(next_ptr3) := io.fire_regid_imm.vd
    next_wport_vd_base(next_ptr3) := io.fire_regid_imm.vd_base
    next_wport_stride(next_ptr3) := io.issue_to_hazard.stride
    next_wport_tvec(next_ptr3) := io.fire_regid_imm.tvec
    next_wport_tag(next_ptr3) := io.fire_regid_imm.pvfb_tag
  }
  when (io.fire.vld)
  {
    next_wport_val(next_ptr3) := Bool(true)
    next_wport_head(next_ptr3) := Bool(true)
    next_wport_vlu(next_ptr3) := Bool(true)
    next_wport_vd(next_ptr3) := io.fire_regid_imm.vd
    next_wport_vd_base(next_ptr3) := io.fire_regid_imm.vd_base
    next_wport_stride(next_ptr3) := io.issue_to_hazard.stride
    next_wport_tvec(next_ptr3) := io.fire_regid_imm.tvec
    next_wport_tag(next_ptr3) := io.fire_regid_imm.pvfb_tag
  }

  when (io.expand_to_hazard.wen)
  {
    next_wport_head(reg_ptr) := Bool(false)
    next_wport_vd(reg_ptr) := array_wport_vd(reg_ptr) + array_wport_stride(reg_ptr)
  }

  when (io.expand_to_hazard.wen_mask)
  {
    next_wmask_head(reg_ptr) := Bool(false)
  }

  when (io.lane_to_hazard.wlast_mask)
  {
    next_wmask_val(reg_ptr) := Bool(false)
  }

  when (io.lane_to_hazard.wlast)
  {
    next_wport_val(reg_ptr) := Bool(false)
    next_wport_vau0(reg_ptr) := Bool(false)
    next_wport_vau1(reg_ptr) := Bool(false)
    next_wport_vau2(reg_ptr) := Bool(false)
    next_wport_vlu(reg_ptr) := Bool(false)
  }

  val array_sport_val = Vec.fill(SZ_BANK){RegReset(Bool(false))}

  val next_sport_val = Vec.fill(SZ_BANK){Bool()}

  for (i <- 0 until SZ_BANK)
  {
    array_sport_val(i) := next_sport_val(i)
  }

  for (i <- 0 until SZ_BANK)
  {
    next_sport_val(i) := array_sport_val(i)
  }

  when (io.fire.viu || io.fire.vau0 || io.fire.vau1 || io.fire.vau2)
  {
    next_sport_val(next_ptr1) := Bool(true)
  }
  when (io.fire.amo)
  {
    next_sport_val(next_ptr1) := Bool(true)
    next_sport_val(next_ptr2) := Bool(true)
    next_sport_val(next_ptr3) := Bool(true)
  }
  when (io.fire.utld || io.fire.utst)
  {
    next_sport_val(next_ptr1) := Bool(true)
    next_sport_val(next_ptr2) := Bool(true)
  }
  when (io.fire.vld)
  {
    next_sport_val(next_ptr1) := Bool(true)
    next_sport_val(next_ptr2) := Bool(true)
  }
  when (io.fire.vst)
  {
    next_sport_val(next_ptr1) := Bool(true)
    next_sport_val(next_ptr2) := Bool(true)
  }
  
  when (io.seq_to_hazard.last)
  {
    next_sport_val(reg_ptr) := Bool(false)
  }

  // hazard check logic for tvec/vt
  val shazard_vau0 = (array_rport_val.toBits & array_rport_vau0.toBits).orR | (array_wport_val.toBits & array_wport_vau0.toBits).orR
  val shazard_vau1 = (array_rport_val.toBits & array_rport_vau1.toBits).orR | (array_wport_val.toBits & array_wport_vau1.toBits).orR
  val shazard_vau2 = (array_rport_val.toBits & array_rport_vau2.toBits).orR | (array_wport_val.toBits & array_wport_vau2.toBits).orR
  val shazard_vgu = (array_rport_val.toBits & array_rport_vgu.toBits).orR
  val shazard_vlu = (array_wport_val.toBits & array_wport_vlu.toBits).orR
  val shazard_vsu = (array_rport_val.toBits & array_rport_vsu.toBits).orR

  val seqhazard_1slot = array_sport_val(next_ptr1)
  val seqhazard_2slot = array_sport_val(next_ptr1) | array_sport_val(next_ptr2)
  val seqhazard_3slot = array_sport_val(next_ptr1) | array_sport_val(next_ptr2) | array_sport_val(next_ptr3)

  // checking any pending memory ops for fences
  io.hazard_to_issue.tvec.pending_memop := array_rport_vsu.toBits.orR || array_rport_vgu.toBits.orR || array_wport_vlu.toBits.orR

  // hazard check logic for tvec
  val tvec_comp_vt =
    Cat(
      io.tvec_regid_imm.vt >= array_wport_vd(7),
      io.tvec_regid_imm.vt >= array_wport_vd(6),
      io.tvec_regid_imm.vt >= array_wport_vd(5),
      io.tvec_regid_imm.vt >= array_wport_vd(4),
      io.tvec_regid_imm.vt >= array_wport_vd(3),
      io.tvec_regid_imm.vt >= array_wport_vd(2),
      io.tvec_regid_imm.vt >= array_wport_vd(1),
      io.tvec_regid_imm.vt >= array_wport_vd(0)
    )

  val tvec_comp_vd =
    Cat(
      io.tvec_regid_imm.vd >= array_wport_vd(7),
      io.tvec_regid_imm.vd >= array_wport_vd(6),
      io.tvec_regid_imm.vd >= array_wport_vd(5),
      io.tvec_regid_imm.vd >= array_wport_vd(4),
      io.tvec_regid_imm.vd >= array_wport_vd(3),
      io.tvec_regid_imm.vd >= array_wport_vd(2),
      io.tvec_regid_imm.vd >= array_wport_vd(1),
      io.tvec_regid_imm.vd >= array_wport_vd(0)
    )

  val tvec_dhazard_vt = (array_wport_val.toBits & tvec_comp_vt).orR
  val tvec_dhazard_vd = (array_wport_val.toBits & tvec_comp_vd).orR

  val tvec_bhazard_r1w1 = array_rport_val(next_ptr2) | array_wport_val(tvec_viu_wptr)
  val tvec_bhazard_vld = array_rport_val(next_ptr2) | array_wport_val(next_ptr3)
  val tvec_bhazard_vst = array_rport_val(next_ptr2) | array_rport_val(next_ptr3) // not sure about this 

  val tvec_dhazard =
    Cat(
      ~io.tvec_regid_imm.vt_zero & tvec_dhazard_vt & io.tvec_dhazard.vt & io.tvec_regid_imm.vt_active,
      tvec_dhazard_vd & io.tvec_dhazard.vd & io.tvec_regid_imm.vd_active
    )

  val tvec_shazard =
    Cat(
      shazard_vgu & io.tvec_shazard.vgu,
      shazard_vlu & io.tvec_shazard.vlu,
      shazard_vsu & io.tvec_shazard.vsu
    )

  val tvec_seqhazard =
    Cat(
      io.tvec_valid.viu & seqhazard_1slot,
      io.tvec_valid.vld & seqhazard_2slot,
      io.tvec_valid.vst & seqhazard_2slot
    )

  val tvec_bhazard =
    Cat(
      tvec_bhazard_r1w1 & io.tvec_bhazard.r1w1,
      tvec_bhazard_vld & io.tvec_bhazard.vld,
      tvec_bhazard_vst & io.tvec_bhazard.vst
    )

  io.tvec_ready := io.tvec_regid_imm.vd_zero || !io.seq_to_hazard.stall && !tvec_dhazard.orR && !tvec_shazard.orR && !tvec_seqhazard.orR && !tvec_bhazard.orR

  val mask_dhazard =
    Cat(
      array_wport_tvec(7) | io.vt_regid_imm.pvfb_tag === array_wport_tag(7),
      array_wport_tvec(6) | io.vt_regid_imm.pvfb_tag === array_wport_tag(6),
      array_wport_tvec(5) | io.vt_regid_imm.pvfb_tag === array_wport_tag(5),
      array_wport_tvec(4) | io.vt_regid_imm.pvfb_tag === array_wport_tag(4),
      array_wport_tvec(3) | io.vt_regid_imm.pvfb_tag === array_wport_tag(3),
      array_wport_tvec(2) | io.vt_regid_imm.pvfb_tag === array_wport_tag(2),
      array_wport_tvec(1) | io.vt_regid_imm.pvfb_tag === array_wport_tag(1),
      array_wport_tvec(0) | io.vt_regid_imm.pvfb_tag === array_wport_tag(0)
    )

  // hazard check logic for vt
  val vt_comp_vs =
    Cat(
      io.vt_regid_imm.vs_base === array_wport_vd_base(7) & io.vt_regid_imm.vs >= array_wport_vd(7) & mask_dhazard(7),
      io.vt_regid_imm.vs_base === array_wport_vd_base(6) & io.vt_regid_imm.vs >= array_wport_vd(6) & mask_dhazard(6),
      io.vt_regid_imm.vs_base === array_wport_vd_base(5) & io.vt_regid_imm.vs >= array_wport_vd(5) & mask_dhazard(5),
      io.vt_regid_imm.vs_base === array_wport_vd_base(4) & io.vt_regid_imm.vs >= array_wport_vd(4) & mask_dhazard(4),
      io.vt_regid_imm.vs_base === array_wport_vd_base(3) & io.vt_regid_imm.vs >= array_wport_vd(3) & mask_dhazard(3),
      io.vt_regid_imm.vs_base === array_wport_vd_base(2) & io.vt_regid_imm.vs >= array_wport_vd(2) & mask_dhazard(2),
      io.vt_regid_imm.vs_base === array_wport_vd_base(1) & io.vt_regid_imm.vs >= array_wport_vd(1) & mask_dhazard(1),
      io.vt_regid_imm.vs_base === array_wport_vd_base(0) & io.vt_regid_imm.vs >= array_wport_vd(0) & mask_dhazard(0)
    )

  val vt_comp_vt = 
    Cat(
      io.vt_regid_imm.vt_base === array_wport_vd_base(7) & io.vt_regid_imm.vt >= array_wport_vd(7) & mask_dhazard(7),
      io.vt_regid_imm.vt_base === array_wport_vd_base(6) & io.vt_regid_imm.vt >= array_wport_vd(6) & mask_dhazard(6),
      io.vt_regid_imm.vt_base === array_wport_vd_base(5) & io.vt_regid_imm.vt >= array_wport_vd(5) & mask_dhazard(5),
      io.vt_regid_imm.vt_base === array_wport_vd_base(4) & io.vt_regid_imm.vt >= array_wport_vd(4) & mask_dhazard(4),
      io.vt_regid_imm.vt_base === array_wport_vd_base(3) & io.vt_regid_imm.vt >= array_wport_vd(3) & mask_dhazard(3),
      io.vt_regid_imm.vt_base === array_wport_vd_base(2) & io.vt_regid_imm.vt >= array_wport_vd(2) & mask_dhazard(2),
      io.vt_regid_imm.vt_base === array_wport_vd_base(1) & io.vt_regid_imm.vt >= array_wport_vd(1) & mask_dhazard(1),
      io.vt_regid_imm.vt_base === array_wport_vd_base(0) & io.vt_regid_imm.vt >= array_wport_vd(0) & mask_dhazard(0)
    )

  val vt_comp_vr =
    Cat(
      io.vt_regid_imm.vr_base === array_wport_vd_base(7) & io.vt_regid_imm.vr >= array_wport_vd(7) & mask_dhazard(7),
      io.vt_regid_imm.vr_base === array_wport_vd_base(6) & io.vt_regid_imm.vr >= array_wport_vd(6) & mask_dhazard(6),
      io.vt_regid_imm.vr_base === array_wport_vd_base(5) & io.vt_regid_imm.vr >= array_wport_vd(5) & mask_dhazard(5),
      io.vt_regid_imm.vr_base === array_wport_vd_base(4) & io.vt_regid_imm.vr >= array_wport_vd(4) & mask_dhazard(4),
      io.vt_regid_imm.vr_base === array_wport_vd_base(3) & io.vt_regid_imm.vr >= array_wport_vd(3) & mask_dhazard(3),
      io.vt_regid_imm.vr_base === array_wport_vd_base(2) & io.vt_regid_imm.vr >= array_wport_vd(2) & mask_dhazard(2),
      io.vt_regid_imm.vr_base === array_wport_vd_base(1) & io.vt_regid_imm.vr >= array_wport_vd(1) & mask_dhazard(1),
      io.vt_regid_imm.vr_base === array_wport_vd_base(0) & io.vt_regid_imm.vr >= array_wport_vd(0) & mask_dhazard(0)
    )

  val vt_comp_vd =
    Cat(
      io.vt_regid_imm.vd_base === array_wport_vd_base(7) & io.vt_regid_imm.vd >= array_wport_vd(7) & mask_dhazard(7),
      io.vt_regid_imm.vd_base === array_wport_vd_base(6) & io.vt_regid_imm.vd >= array_wport_vd(6) & mask_dhazard(6),
      io.vt_regid_imm.vd_base === array_wport_vd_base(5) & io.vt_regid_imm.vd >= array_wport_vd(5) & mask_dhazard(5),
      io.vt_regid_imm.vd_base === array_wport_vd_base(4) & io.vt_regid_imm.vd >= array_wport_vd(4) & mask_dhazard(4),
      io.vt_regid_imm.vd_base === array_wport_vd_base(3) & io.vt_regid_imm.vd >= array_wport_vd(3) & mask_dhazard(3),
      io.vt_regid_imm.vd_base === array_wport_vd_base(2) & io.vt_regid_imm.vd >= array_wport_vd(2) & mask_dhazard(2),
      io.vt_regid_imm.vd_base === array_wport_vd_base(1) & io.vt_regid_imm.vd >= array_wport_vd(1) & mask_dhazard(1),
      io.vt_regid_imm.vd_base === array_wport_vd_base(0) & io.vt_regid_imm.vd >= array_wport_vd(0) & mask_dhazard(0)
    )

  val vt_dhazard_vs = (array_wport_val.toBits & vt_comp_vs).orR
  val vt_dhazard_vt = (array_wport_val.toBits & vt_comp_vt).orR
  val vt_dhazard_vr = (array_wport_val.toBits & vt_comp_vr).orR
  val vt_dhazard_vd = (array_wport_val.toBits & vt_comp_vd).orR

  val vt_bhazard_r2wm = array_rport_val(next_ptr2) | array_rport_val(next_ptr3) | array_wmask_val(vbr_wptr)
  val vt_bhazard_r1w1 = array_rport_val(next_ptr2) | array_wport_val(vt_wptr)
  val vt_bhazard_r2w1 = array_rport_val(next_ptr2) | array_rport_val(next_ptr3) | array_wport_val(vt_wptr)
  val vt_bhazard_r3w1 = array_rport_val(next_ptr2) | array_rport_val(next_ptr3) | array_rport_val(next_ptr4) | array_wport_val(vt_wptr)
  val vt_bhazard_amo = array_rport_val(next_ptr2) | array_rport_val(next_ptr3) | array_wport_val(next_ptr4)
  val vt_bhazard_utld = array_rport_val(next_ptr2) | array_wport_val(next_ptr3)
  val vt_bhazard_utst = array_rport_val(next_ptr2) | array_rport_val(next_ptr3)

  val vt_dhazard =
    Cat(
      ~io.vt_regid_imm.vs_zero & vt_dhazard_vs & io.vt_dhazard.vs & io.vt_regid_imm.vs_active,
      ~io.vt_regid_imm.vt_zero & vt_dhazard_vt & io.vt_dhazard.vt & io.vt_regid_imm.vt_active,
      ~io.vt_regid_imm.vr_zero & vt_dhazard_vr & io.vt_dhazard.vr & io.vt_regid_imm.vr_active,
      vt_dhazard_vd & io.vt_dhazard.vd & io.vt_regid_imm.vd_active
    )

  val vt_shazard =
    Cat(
      shazard_vau0 & io.vt_shazard.vau0,
      shazard_vau1 & io.vt_shazard.vau1,
      shazard_vau2 & io.vt_shazard.vau2,
      shazard_vgu & io.vt_shazard.vgu,
      shazard_vlu & io.vt_shazard.vlu,
      shazard_vsu & io.vt_shazard.vsu
    )

  val vt_seqhazard =
    Cat(
      io.vt_valid.viu & seqhazard_1slot,
      io.vt_valid.vau0 & seqhazard_1slot,
      io.vt_valid.vau1 & seqhazard_1slot,
      io.vt_valid.vau2 & seqhazard_1slot,
      io.vt_valid.amo & seqhazard_3slot,
      io.vt_valid.utld & seqhazard_2slot,
      io.vt_valid.utst & seqhazard_2slot
    )

  val vt_bhazard =
    Cat(
      vt_bhazard_r2wm & io.vt_bhazard.r2wm,
      vt_bhazard_r1w1 & io.vt_bhazard.r1w1,
      vt_bhazard_r2w1 & io.vt_bhazard.r2w1,
      vt_bhazard_r3w1 & io.vt_bhazard.r3w1,
      vt_bhazard_amo & io.vt_bhazard.amo,
      vt_bhazard_utld & io.vt_bhazard.utld,
      vt_bhazard_utst & io.vt_bhazard.utst
    )

  io.vt_ready := io.vt_regid_imm.vd_zero || !io.seq_to_hazard.stall && !vt_dhazard.orR && !vt_shazard.orR && !vt_seqhazard.orR && !vt_bhazard.orR
}
