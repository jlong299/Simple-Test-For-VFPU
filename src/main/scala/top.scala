// src/main/scala/top.scala
package top

import chisel3._
import chisel3.util._
import chisel3.stage._

import Vreduction._
import vector._

class top extends Module with Params{


  val io = IO(new Bundle {
    val fire          = Input(Bool())
    val is_vfredsum   = Input(Bool())
    val index         = Input(UInt(3.W))  
    val is_vfredmax   = Input(Bool())
    val opcode        = Input(UInt(4.W))
    val vlmul         = Input(UInt(3.W))
    val mask          = Input(Vec(VLEN/XLEN, UInt(XLEN.W)))
    val round_mode    = Input(UInt(3.W))
    val fp_format     = Input(UInt(2.W))
    val is_vec        = Input(Bool())
    val vs2           = Input(Vec(VLEN/XLEN, UInt(XLEN.W)))
    val vs1           = Input(Vec(VLEN/XLEN, UInt(XLEN.W)))

    val vd  = Output(UInt(XLEN.W))
    val fflags  = Output(UInt(5.W))
  })

  // val mix_fmul = Module(new RIGHT_FloatFMA)
  // mix_fmul.io.fire := io.fire
  // mix_fmul.io.fp_a := Cat(0.U((64-32).W), io.vs1(0)(32-1, 0))
  // mix_fmul.io.fp_b := Cat(0.U((64-32).W), io.vs2(0)(32-1, 0))
  // mix_fmul.io.fp_c := 0.U

  // mix_fmul.io.round_mode := io.round_mode
  // mix_fmul.io.fp_format := io.fp_format
  // mix_fmul.io.op_code := io.opcode
  // mix_fmul.io.fp_aIsFpCanonicalNAN := 0.U 
  // mix_fmul.io.fp_bIsFpCanonicalNAN := 0.U 
  // mix_fmul.io.fp_cIsFpCanonicalNAN := 0.U 

  // io.right_vd := mix_fmul.io.fp_result  
  // io.right_fflags := mix_fmul.io.fflags

  val fmul = Module(new GFloatFMA)
  fmul.io.fire := io.fire
  fmul.io.fp_a := io.vs1(0)(floatWidth-1, 0)
  fmul.io.fp_b := io.vs2(0)(floatWidth-1, 0)
  fmul.io.fp_c := 0.U

  fmul.io.round_mode := io.round_mode
  fmul.io.fp_format := io.fp_format
  fmul.io.op_code := io.opcode
  fmul.io.fp_aIsFpCanonicalNAN := 0.U 
  fmul.io.fp_bIsFpCanonicalNAN := 0.U 
  fmul.io.fp_cIsFpCanonicalNAN := 0.U 

  io.vd := fmul.io.fp_result  
  io.fflags := fmul.io.fflags

}

object topMain extends App {
  (new ChiselStage).emitVerilog(new top, args)
}