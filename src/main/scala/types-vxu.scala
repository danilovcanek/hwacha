package hwacha

import Chisel._

abstract class VXUModule(clock: Clock = null, _reset: Bool = null)
  extends HwachaModule(clock, _reset) with SeqParameters with LaneParameters with DCCParameters with ExpParameters
abstract class VXUBundle
  extends HwachaBundle with SeqParameters with LaneParameters with DCCParameters with ExpParameters

//-------------------------------------------------------------------------\\
// vector functional unit fn types
//-------------------------------------------------------------------------\\

class VIUFn extends VXUBundle {
  val dw = Bits(width = SZ_DW)
  val fp = Bits(width = SZ_FP)
  val op = Bits(width = SZ_VIU_OP)

  def dgate(valid: Bool) = this.clone.fromBits(DataGating.dgate(valid, this.toBits))

  def dw_is(_dw: UInt) = dw === _dw
  def fp_is(fps: UInt*) = fps.toList.map(x => {fp === x}).reduceLeft(_ || _)
  def op_is(ops: UInt*) = ops.toList.map(x => {op === x}).reduceLeft(_ || _)
}

class VIPUFn extends VXUBundle {
  val op = Bits(width = SZ_VIPU_OP)
}

class VIXUFn(sz_op: Int) extends VXUBundle {
  val dw = UInt(width = SZ_DW)
  val op = UInt(width = sz_op)

  def dgate(valid: Bool) = this.clone.fromBits(DataGating.dgate(valid, this.toBits))

  def dw_is(_dw: UInt) = dw === _dw
  def op_is(ops: UInt*): Bool = op_is(ops.toList)
  def op_is(ops: List[UInt]): Bool = ops.toList.map(x => {op === x}).reduce(_ || _)
  def is(_dw: UInt, ops: UInt*) = dw_is(_dw) && op_is(ops.toList)
}

class VIMUFn extends VIXUFn(SZ_VIMU_OP)
class VIDUFn extends VIXUFn(SZ_VIDU_OP)

class VFXUFn(sz_op: Int) extends VXUBundle {
  val fp = UInt(width = SZ_FP)
  val rm = UInt(width = rocket.FPConstants.RM_SZ)
  val op = UInt(width = sz_op)

  def dgate(valid: Bool) = this.clone.fromBits(DataGating.dgate(valid, this.toBits))

  def fp_is(fps: UInt*) = fps.toList.map(x => {fp === x}).reduceLeft(_ || _)
  def op_is(ops: UInt*) = ops.toList.map(x => {op === x}).reduceLeft(_ || _)
}

class VFMUFn extends VFXUFn(SZ_VFMU_OP)
class VFDUFn extends VFXUFn(SZ_VFDU_OP)
class VFCUFn extends VFXUFn(SZ_VFCU_OP)
class VFVUFn extends VFXUFn(SZ_VFVU_OP)

class VQUFn extends VXUBundle {
  val latch = Bits(width = 2)
}

class VFn extends VXUBundle {
  val union = Bits(width = List(
    new VIUFn().toBits.getWidth,
    new VIPUFn().toBits.getWidth,
    new VIMUFn().toBits.getWidth,
    new VIDUFn().toBits.getWidth,
    new VFMUFn().toBits.getWidth,
    new VFDUFn().toBits.getWidth,
    new VFCUFn().toBits.getWidth,
    new VFVUFn().toBits.getWidth,
    new VMUFn().toBits.getWidth,
    new VQUFn().toBits.getWidth).reduceLeft((x, y) => if (x > y) x else y)
  )

  def viu(d: Int = 0) = new VIUFn().fromBits(this.union)
  def vipu(d: Int = 0) = new VIPUFn().fromBits(this.union)
  def vimu(d: Int = 0) = new VIMUFn().fromBits(this.union)
  def vidu(d: Int = 0) = new VIDUFn().fromBits(this.union)
  def vfmu(d: Int = 0) = new VFMUFn().fromBits(this.union)
  def vfdu(d: Int = 0) = new VFDUFn().fromBits(this.union)
  def vfcu(d: Int = 0) = new VFCUFn().fromBits(this.union)
  def vfvu(d: Int = 0) = new VFVUFn().fromBits(this.union)
  def vmu(d: Int = 0) = new VMUFn().fromBits(this.union)
  def vqu(d: Int = 0) = new VQUFn().fromBits(this.union)
}


//-------------------------------------------------------------------------\\
// decoded information types
//-------------------------------------------------------------------------\\

class RegInfo extends VXUBundle {
  val valid = Bool()
  val scalar = Bool()
  val pred = Bool()
  val id = UInt(width = math.max(bRFAddr, bPredAddr))

  def is_scalar(d: Int = 0) = !pred && scalar
  def is_vector(d: Int = 0) = !pred && !scalar
  def is_pred(d: Int = 0) = pred
  def neg(d: Int = 0) = scalar
}

class DecodedRegisters extends VXUBundle {
  val vp = new RegInfo
  val vs1 = new RegInfo
  val vs2 = new RegInfo
  val vs3 = new RegInfo
  val vd = new RegInfo
}

class ScalarRegisters extends VXUBundle {
  val ss1 = Bits(width = regLen)
  val ss2 = Bits(width = regLen)
  val ss3 = Bits(width = regLen)
}

class DecodedInstruction extends VXUBundle {
  val fn = new VFn // union
  val reg = new DecodedRegisters
  val sreg = new ScalarRegisters
}


//-------------------------------------------------------------------------\\
// issue op
//-------------------------------------------------------------------------\\

class IssueType extends VXUBundle {
  val vint = Bool()
  val vipred = Bool()
  val vimul = Bool()
  val vidiv = Bool()
  val vfma = Bool()
  val vfdiv = Bool()
  val vfcmp = Bool()
  val vfconv = Bool()
  val vamo = Bool()
  val vldx = Bool()
  val vstx = Bool()
  val vld = Bool()
  val vst = Bool()

  def enq_vdu(dummy: Int = 0) = vidiv || vfdiv
  def enq_vgu(dummy: Int = 0) = vamo || vldx || vstx
  def enq_vpu(dummy: Int = 0) = vamo || vldx || vstx || vld || vst
  def enq_vlu(dummy: Int = 0) = vamo || vldx || vld
  def enq_vsu(dummy: Int = 0) = vamo || vstx || vst
  def enq_dcc(dummy: Int = 0) = enq_vdu() || enq_vgu() || enq_vpu() || enq_vlu() || enq_vsu()
}

class IssueOp extends DecodedInstruction {
  val vlen = UInt(width = bVLen)
  val active = new IssueType
}


//-------------------------------------------------------------------------\\
// traits
//-------------------------------------------------------------------------\\

trait LaneOp extends VXUBundle {
  val strip = UInt(width = bStrip)
}

trait BankPred extends VXUBundle {
  val pred = Bits(width = nSlices)
  def active(dummy: Int = 0) = pred.orR
  def neg(cond: Bool) = Mux(cond, ~pred, pred)
}

trait BankMask extends VXUBundle {
  val mask = Bits(width = wBank/8)
}

trait BankData extends VXUBundle {
  val data = Bits(width = wBank)
}

trait MicroOp extends BankPred

//-------------------------------------------------------------------------\\
// sequencer op
//-------------------------------------------------------------------------\\

class SeqType extends VXUBundle {
  val viu = Bool()
  val vipu = Bool()
  val vimu = Bool()
  val vidu = Bool()
  val vfmu = Bool()
  val vfdu = Bool()
  val vfcu = Bool()
  val vfvu = Bool()
  val vpu = Bool()
  val vgu = Bool()
  val vcu = Bool()
  val vlu = Bool()
  val vsu = Bool()
  val vqu = Bool()
}

class SeqEntry extends DecodedInstruction {
  val active = new SeqType
  val base = new DecodedRegisters
  val raw = Vec.fill(nSeq){Bool()}
  val war = Vec.fill(nSeq){Bool()}
  val waw = Vec.fill(nSeq){Bool()}
  val rports = UInt(width = bRPorts)
  val wport = new Bundle {
    val sram = UInt(width = bWPortLatency)
    val pred = UInt(width = bPredWPortLatency)
  }
  val vlen = UInt(width = bVLen)
  val eidx = UInt(width = bVLen)
  val age = UInt(width = log2Up(nBanks))
}

class SeqSelect extends VXUBundle {
  val vfmu = UInt(width = log2Up(nVFMU))
}

class SeqOp extends DecodedInstruction with LaneOp {
  val active = new SeqType
  val select = new SeqSelect
  val eidx = UInt(width = bVLen)
  val rports = UInt(width = bRPorts)
  val wport = new Bundle {
    val sram = UInt(width = bWPortLatency)
    val pred = UInt(width = bPredWPortLatency)
  }

  def active_vfmu(i: Int) = active.vfmu && select.vfmu === UInt(i)
}

class SeqVPUOp extends DecodedInstruction with LaneOp
class SeqVIPUOp extends DecodedInstruction with LaneOp


//-------------------------------------------------------------------------\\
// lane, micro op
//-------------------------------------------------------------------------\\

class SRAMRFReadOp extends VXUBundle {
  val addr = UInt(width = log2Up(nSRAM))
}

class SRAMRFWriteOp extends VXUBundle {
  val addr = UInt(width = log2Up(nSRAM))
  val selg = Bool()
  val wsel = UInt(width = log2Up(nWSel))
}

class FFRFReadOp extends VXUBundle {
  val addr = UInt(width = log2Up(nFF))
}

class FFRFWriteOp extends VXUBundle {
  val addr = UInt(width = log2Up(nFF))
  val selg = Bool()
  val wsel = UInt(width = log2Up(nWSel))
}

class PredRFReadOp extends VXUBundle {
  val addr = UInt(width = log2Up(nPred))
}

class PredRFGatedReadOp extends PredRFReadOp {
  val off = Bool()
  val neg = Bool()
}

class PredRFWriteOp extends VXUBundle {
  val addr = UInt(width = log2Up(nPred))
  val selg = Bool()
  val plu = Bool()
}

class OPLOp extends VXUBundle {
  val selff = Bool()
}

class PDLOp extends VXUBundle

class SRegOp extends VXUBundle {
  val operand = Bits(width = regLen)
}

class XBarOp extends VXUBundle {
  val pdladdr = UInt(width = log2Up(nGPDL))
}

class PXBarOp extends VXUBundle

class VIUOp extends VXUBundle {
  val fn = new VIUFn
  val eidx = UInt(width = bVLen)
}

class VIPUOp extends VXUBundle {
  val fn = new VIPUFn
}

case class SharedLLOp(nOperands: Int) extends VXUBundle {
  val sreg = Vec.fill(nOperands){Bool()}
}

class VIMUOp extends SharedLLOp(2) {
  val fn = new VIMUFn
}

class VFMUOp extends SharedLLOp(3) {
  val fn = new VFMUFn
}

class VFCUOp extends SharedLLOp(2) {
  val fn = new VFCUFn
}

class VFVUOp extends SharedLLOp(1) {
  val fn = new VFVUFn
}

class VQUOp extends SharedLLOp(2) {
  val fn = new VQUFn
}

class VPUOp extends VXUBundle

class VGUOp extends SharedLLOp(1) {
  val fn = new VMUFn
}

class VSUOp extends VXUBundle {
  val selff = Bool() // select ff if true
}

//-------------------------------------------------------------------------\\
// lane op
//-------------------------------------------------------------------------\\

class SRAMRFReadLaneOp extends SRAMRFReadOp with LaneOp
class SRAMRFWriteLaneOp extends SRAMRFWriteOp with LaneOp
class FFRFReadLaneOp extends FFRFReadOp with LaneOp
class FFRFWriteLaneOp extends FFRFWriteOp with LaneOp
class PredRFReadLaneOp extends PredRFReadOp with LaneOp
class PredRFGatedReadLaneOp extends PredRFGatedReadOp with LaneOp
class PredRFWriteLaneOp extends PredRFWriteOp with LaneOp
class OPLLaneOp extends OPLOp with LaneOp
class PDLLaneOp extends PDLOp with LaneOp
class SRegLaneOp extends SRegOp with LaneOp
class XBarLaneOp extends XBarOp with LaneOp
class PXBarLaneOp extends PXBarOp with LaneOp
class VIULaneOp extends VIUOp with LaneOp
class VIPULaneOp extends VIPUOp with LaneOp
class VIMULaneOp extends VIMUOp with LaneOp
class VFMULaneOp extends VFMUOp with LaneOp
class VFCULaneOp extends VFCUOp with LaneOp
class VFVULaneOp extends VFVUOp with LaneOp
class VQULaneOp extends VQUOp with LaneOp
class VPULaneOp extends VPUOp with LaneOp
class VGULaneOp extends VGUOp with LaneOp
class VSULaneOp extends VSUOp with LaneOp

class SRAMRFReadExpEntry extends SRAMRFReadLaneOp {
  val global = new VXUBundle {
    val valid = Bool()
    val id = UInt(width = log2Up(nGOPL))
  }
  val local = new VXUBundle {
    val valid = Bool()
    val id = UInt(width = log2Up(nLOPL))
  }
}
class SRAMRFWriteExpEntry extends SRAMRFWriteLaneOp

class PredRFReadExpEntry extends PredRFGatedReadLaneOp {
  val global = new VXUBundle {
    val valid = Bool()
    val id = UInt(width = log2Up(nGPDL))
  }
  val local = new VXUBundle {
    val valid = Bool()
    val id = UInt(width = log2Up(nLPDL))
  }
}

class PredRFWriteExpEntry extends PredRFWriteLaneOp

//-------------------------------------------------------------------------\\
// banks
//-------------------------------------------------------------------------\\

class BankPredEntry extends BankPred
class BankDataEntry extends BankData
class BankDataPredEntry extends BankData with BankPred

//-------------------------------------------------------------------------\\
// micro op
//-------------------------------------------------------------------------\\

class SRAMRFReadMicroOp extends SRAMRFReadOp with MicroOp
class SRAMRFWriteMicroOp extends SRAMRFWriteOp with MicroOp
class FFRFReadMicroOp extends FFRFReadOp with MicroOp
class FFRFWriteMicroOp extends FFRFWriteOp with MicroOp
class PredRFReadMicroOp extends PredRFReadOp with MicroOp
class PredRFGatedReadMicroOp extends PredRFGatedReadOp with MicroOp
class PredRFWriteMicroOp extends PredRFWriteOp with MicroOp
class OPLMicroOp extends OPLOp with MicroOp
class PDLMicroOp extends PDLOp with MicroOp
class SRegMicroOp extends SRegOp with MicroOp
class XBarMicroOp extends XBarOp with MicroOp
class PXBarMicroOp extends PXBarOp with MicroOp
class VIUMicroOp extends VIUOp with MicroOp
class VIPUMicroOp extends VIPUOp with MicroOp
class VIMUMicroOp extends VIMUOp with MicroOp
class VFMUMicroOp extends VFMUOp with MicroOp
class VFCUMicroOp extends VFCUOp with MicroOp
class VFVUMicroOp extends VFVUOp with MicroOp
class VQUMicroOp extends VQUOp with MicroOp
class VPUMicroOp extends VPUOp with MicroOp
class VGUMicroOp extends VGUOp with MicroOp
class VSUMicroOp extends VSUOp with MicroOp

//-------------------------------------------------------------------------\\
// bank acks
//-------------------------------------------------------------------------\\

class VIUAck extends BankPred
class VIPUAck extends BankPred
class VIMUAck extends BankPred
class VIDUAck extends BankPred
class VGUAck extends BankPred
class VQUAck extends BankPred

class VFXUAck extends VXUBundle with BankPred {
  val exc = Bits(OUTPUT, rocket.FPConstants.FLAGS_SZ)
}

class VFMUAck extends VFXUAck
class VFDUAck extends VFXUAck
class VFCUAck extends BankPred // no exceptions can occur
class VFVUAck extends VFXUAck


//-------------------------------------------------------------------------\\
// decoupled cluster (dcc) types
//-------------------------------------------------------------------------\\

class DCCOp extends VXUBundle {
  val vlen = UInt(width = bVLen)
  val active = new IssueType
  val fn = new VFn
  val vd = new RegInfo
}

class LPQEntry extends VXUBundle with BankPred
class BPQEntry extends VXUBundle with BankPred
class LRQEntry extends VXUBundle with BankData
class BRQEntry extends VXUBundle with BankData
class BWQEntry extends VXUBundle with BankData with BankMask {
  val selff = Bool() // select ff if true
  val addr = UInt(width = math.max(log2Up(nSRAM), log2Up(nFF)))

  def saddr(dummy: Int = 0) = addr(log2Up(nSRAM)-1, 0)
  def faddr(dummy: Int = 0) = addr(log2Up(nFF)-1, 0)
}
