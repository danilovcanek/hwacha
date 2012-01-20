package riscvVector
{

import Chisel._
import Node._
import Config._

class vuVXU_Pointer extends Component
{
  val io = new Bundle
  {
    val ptr = UFix(DEF_BPTR, INPUT);
    val incr = UFix(DEF_BPTR1, INPUT);
    val bcnt = UFix(DEF_BCNT, INPUT);
    val nptr = UFix(DEF_BPTR, OUTPUT);
  }

  val add = ptr + incr;

  io.nptr := MuxLookup(
    Cat(add, bcnt), Bits(0, SZ_BPTR), Array(
      Cat(UFix(0,5),UFix(3,4)) -> UFix(0,3),
      Cat(UFix(0,5),UFix(4,4)) -> UFix(0,3),
      Cat(UFix(0,5),UFix(5,4)) -> UFix(0,3),
      Cat(UFix(0,5),UFix(6,4)) -> UFix(0,3),
      Cat(UFix(0,5),UFix(7,4)) -> UFix(0,3),
      Cat(UFix(0,5),UFix(8,4)) -> UFix(0,3),
      Cat(UFix(1,5),UFix(3,4)) -> UFix(1,3),
      Cat(UFix(1,5),UFix(4,4)) -> UFix(1,3),
      Cat(UFix(1,5),UFix(5,4)) -> UFix(1,3),
      Cat(UFix(1,5),UFix(6,4)) -> UFix(1,3),
      Cat(UFix(1,5),UFix(7,4)) -> UFix(1,3),
      Cat(UFix(1,5),UFix(8,4)) -> UFix(1,3),
      Cat(UFix(2,5),UFix(3,4)) -> UFix(2,3),
      Cat(UFix(2,5),UFix(4,4)) -> UFix(2,3),
      Cat(UFix(2,5),UFix(5,4)) -> UFix(2,3),
      Cat(UFix(2,5),UFix(6,4)) -> UFix(2,3),
      Cat(UFix(2,5),UFix(7,4)) -> UFix(2,3),
      Cat(UFix(2,5),UFix(8,4)) -> UFix(2,3),
      Cat(UFix(3,5),UFix(3,4)) -> UFix(0,3),
      Cat(UFix(3,5),UFix(4,4)) -> UFix(3,3),
      Cat(UFix(3,5),UFix(5,4)) -> UFix(3,3),
      Cat(UFix(3,5),UFix(6,4)) -> UFix(3,3),
      Cat(UFix(3,5),UFix(7,4)) -> UFix(3,3),
      Cat(UFix(3,5),UFix(8,4)) -> UFix(3,3),
      Cat(UFix(4,5),UFix(3,4)) -> UFix(1,3),
      Cat(UFix(4,5),UFix(4,4)) -> UFix(0,3),
      Cat(UFix(4,5),UFix(5,4)) -> UFix(4,3),
      Cat(UFix(4,5),UFix(6,4)) -> UFix(4,3),
      Cat(UFix(4,5),UFix(7,4)) -> UFix(4,3),
      Cat(UFix(4,5),UFix(8,4)) -> UFix(4,3),
      Cat(UFix(5,5),UFix(3,4)) -> UFix(2,3),
      Cat(UFix(5,5),UFix(4,4)) -> UFix(1,3),
      Cat(UFix(5,5),UFix(5,4)) -> UFix(0,3),
      Cat(UFix(5,5),UFix(6,4)) -> UFix(5,3),
      Cat(UFix(5,5),UFix(7,4)) -> UFix(5,3),
      Cat(UFix(5,5),UFix(8,4)) -> UFix(5,3),
      Cat(UFix(6,5),UFix(3,4)) -> UFix(0,3),
      Cat(UFix(6,5),UFix(4,4)) -> UFix(2,3),
      Cat(UFix(6,5),UFix(5,4)) -> UFix(1,3),
      Cat(UFix(6,5),UFix(6,4)) -> UFix(0,3),
      Cat(UFix(6,5),UFix(7,4)) -> UFix(6,3),
      Cat(UFix(6,5),UFix(8,4)) -> UFix(6,3),
      Cat(UFix(7,5),UFix(3,4)) -> UFix(1,3),
      Cat(UFix(7,5),UFix(4,4)) -> UFix(3,3),
      Cat(UFix(7,5),UFix(5,4)) -> UFix(2,3),
      Cat(UFix(7,5),UFix(6,4)) -> UFix(1,3),
      Cat(UFix(7,5),UFix(7,4)) -> UFix(0,3),
      Cat(UFix(7,5),UFix(8,4)) -> UFix(7,3),
      Cat(UFix(8,5),UFix(3,4)) -> UFix(2,3),
      Cat(UFix(8,5),UFix(4,4)) -> UFix(0,3),
      Cat(UFix(8,5),UFix(5,4)) -> UFix(3,3),
      Cat(UFix(8,5),UFix(6,4)) -> UFix(2,3),
      Cat(UFix(8,5),UFix(7,4)) -> UFix(1,3),
      Cat(UFix(8,5),UFix(8,4)) -> UFix(0,3),
      Cat(UFix(9,5),UFix(3,4)) -> UFix(0,3),
      Cat(UFix(9,5),UFix(4,4)) -> UFix(1,3),
      Cat(UFix(9,5),UFix(5,4)) -> UFix(4,3),
      Cat(UFix(9,5),UFix(6,4)) -> UFix(3,3),
      Cat(UFix(9,5),UFix(7,4)) -> UFix(2,3),
      Cat(UFix(9,5),UFix(8,4)) -> UFix(1,3),
      Cat(UFix(10,5),UFix(3,4)) -> UFix(1,3),
      Cat(UFix(10,5),UFix(4,4)) -> UFix(2,3),
      Cat(UFix(10,5),UFix(5,4)) -> UFix(0,3),
      Cat(UFix(10,5),UFix(6,4)) -> UFix(4,3),
      Cat(UFix(10,5),UFix(7,4)) -> UFix(3,3),
      Cat(UFix(10,5),UFix(8,4)) -> UFix(2,3),
      Cat(UFix(11,5),UFix(3,4)) -> UFix(2,3),
      Cat(UFix(11,5),UFix(4,4)) -> UFix(3,3),
      Cat(UFix(11,5),UFix(5,4)) -> UFix(1,3),
      Cat(UFix(11,5),UFix(6,4)) -> UFix(5,3),
      Cat(UFix(11,5),UFix(7,4)) -> UFix(4,3),
      Cat(UFix(11,5),UFix(8,4)) -> UFix(3,3),
      Cat(UFix(12,5),UFix(3,4)) -> UFix(0,3),
      Cat(UFix(12,5),UFix(4,4)) -> UFix(0,3),
      Cat(UFix(12,5),UFix(5,4)) -> UFix(2,3),
      Cat(UFix(12,5),UFix(6,4)) -> UFix(0,3),
      Cat(UFix(12,5),UFix(7,4)) -> UFix(5,3),
      Cat(UFix(12,5),UFix(8,4)) -> UFix(4,3),
      Cat(UFix(13,5),UFix(3,4)) -> UFix(1,3),
      Cat(UFix(13,5),UFix(4,4)) -> UFix(1,3),
      Cat(UFix(13,5),UFix(5,4)) -> UFix(3,3),
      Cat(UFix(13,5),UFix(6,4)) -> UFix(1,3),
      Cat(UFix(13,5),UFix(7,4)) -> UFix(6,3),
      Cat(UFix(13,5),UFix(8,4)) -> UFix(5,3),
      Cat(UFix(14,5),UFix(3,4)) -> UFix(2,3),
      Cat(UFix(14,5),UFix(4,4)) -> UFix(2,3),
      Cat(UFix(14,5),UFix(5,4)) -> UFix(4,3),
      Cat(UFix(14,5),UFix(6,4)) -> UFix(2,3),
      Cat(UFix(14,5),UFix(7,4)) -> UFix(0,3),
      Cat(UFix(14,5),UFix(8,4)) -> UFix(6,3),
      Cat(UFix(15,5),UFix(3,4)) -> UFix(0,3),
      Cat(UFix(15,5),UFix(4,4)) -> UFix(3,3),
      Cat(UFix(15,5),UFix(5,4)) -> UFix(0,3),
      Cat(UFix(15,5),UFix(6,4)) -> UFix(3,3),
      Cat(UFix(15,5),UFix(7,4)) -> UFix(1,3),
      Cat(UFix(15,5),UFix(8,4)) -> UFix(7,3),
      Cat(UFix(16,5),UFix(3,4)) -> UFix(1,3),
      Cat(UFix(16,5),UFix(4,4)) -> UFix(0,3),
      Cat(UFix(16,5),UFix(5,4)) -> UFix(1,3),
      Cat(UFix(16,5),UFix(6,4)) -> UFix(4,3),
      Cat(UFix(16,5),UFix(7,4)) -> UFix(2,3),
      Cat(UFix(16,5),UFix(8,4)) -> UFix(0,3),
      Cat(UFix(17,5),UFix(3,4)) -> UFix(2,3),
      Cat(UFix(17,5),UFix(4,4)) -> UFix(1,3),
      Cat(UFix(17,5),UFix(5,4)) -> UFix(2,3),
      Cat(UFix(17,5),UFix(6,4)) -> UFix(5,3),
      Cat(UFix(17,5),UFix(7,4)) -> UFix(3,3),
      Cat(UFix(17,5),UFix(8,4)) -> UFix(1,3)
      //Cat(UFix(18,5),UFix(3,4)) -> UFix(0,3),
      //Cat(UFix(18,5),UFix(4,4)) -> UFix(2,3),
      //Cat(UFix(18,5),UFix(5,4)) -> UFix(3,3),
      //Cat(UFix(18,5),UFix(6,4)) -> UFix(0,3),
      //Cat(UFix(18,5),UFix(7,4)) -> UFix(4,3),
      //Cat(UFix(18,5),UFix(8,4)) -> UFix(2,3),
      //Cat(UFix(19,5),UFix(3,4)) -> UFix(1,3),
      //Cat(UFix(19,5),UFix(4,4)) -> UFix(3,3),
      //Cat(UFix(19,5),UFix(5,4)) -> UFix(4,3),
      //Cat(UFix(19,5),UFix(6,4)) -> UFix(1,3),
      //Cat(UFix(19,5),UFix(7,4)) -> UFix(5,3),
      //Cat(UFix(19,5),UFix(8,4)) -> UFix(3,3),
      //Cat(UFix(20,5),UFix(3,4)) -> UFix(2,3),
      //Cat(UFix(20,5),UFix(4,4)) -> UFix(0,3),
      //Cat(UFix(20,5),UFix(5,4)) -> UFix(0,3),
      //Cat(UFix(20,5),UFix(6,4)) -> UFix(2,3),
      //Cat(UFix(20,5),UFix(7,4)) -> UFix(6,3),
      //Cat(UFix(20,5),UFix(8,4)) -> UFix(4,3),
      //Cat(UFix(21,5),UFix(3,4)) -> UFix(0,3),
      //Cat(UFix(21,5),UFix(4,4)) -> UFix(1,3),
      //Cat(UFix(21,5),UFix(5,4)) -> UFix(1,3),
      //Cat(UFix(21,5),UFix(6,4)) -> UFix(3,3),
      //Cat(UFix(21,5),UFix(7,4)) -> UFix(0,3),
      //Cat(UFix(21,5),UFix(8,4)) -> UFix(5,3)
    ));
}

}
