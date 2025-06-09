// src/main/scala/top.scala
package topredu

import chisel3._
import chisel3.util._
import chisel3.stage._
import Vreduction._
import Vreduction.Params._

class top extends Module{
  val io = IO(new Bundle {
    val fire          = Input(Bool())
    val is_vfredsum   = Input(Bool())
    val index         = Input(UInt(3.W))  
    val is_vfredmax   = Input(Bool())
    val vlmul         = Input(UInt(3.W))
    val mask          = Input(Vec(VLEN/XLEN, UInt(XLEN.W)))
    val round_mode    = Input(UInt(3.W))
    val fp_format     = Input(UInt(2.W))
    val is_vec        = Input(Bool())
    val vs2           = Input(Vec(VLEN/XLEN, UInt(XLEN.W)))
    val vs1           = Input(Vec(VLEN/XLEN, UInt(XLEN.W)))
    
    val vd            = Output(UInt(XLEN.W))
    val fflags        = Output(UInt(5.W))
    val finish        = Output(Bool())
  })

  val opcode = Mux(io.is_vfredsum, VfaddOpCode.fadd, VfaddOpCode.fmax)

  val vfred = Module(new Vfreduction)
  vfred.io.fire      := io.fire      
  vfred.io.in.vlmul     := io.vlmul     
  vfred.io.in.mask      := io.mask.asTypeOf(vfred.io.in.mask)      
  vfred.io.in.round_mode:= io.round_mode
  vfred.io.in.fp_format := io.fp_format 
  vfred.io.in.op_code   := opcode   
  vfred.io.in.is_vec    := io.is_vec    
  vfred.io.in.index     := io.index     
  vfred.io.in.vs1       := io.vs1(0)  
  vfred.io.in.vs2       := io.vs2.asTypeOf(vfred.io.in.vs2)

  io.vd := vfred.io.out.result.asTypeOf(io.vd)
  io.fflags := vfred.io.out.fflags     
  io.finish := vfred.io.finish
}

object topMain extends App {
  (new ChiselStage).emitVerilog(new top, args)
}