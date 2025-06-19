// src/main/scala/top.scala
package top

import chisel3._
import chisel3.util._
import chisel3.stage._
import race.vpu._
import race.vpu.VParams._
import race.vpu.exu.laneexu.fp._

class top extends Module{
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val is_bf16, is_fp16, is_fp32 = Input(Bool())
    val is_widen = Input(Bool())
    val a_in_32 = Input(UInt(32.W))
    val b_in_32 = Input(UInt(32.W))
    val c_in_32 = Input(UInt(32.W))
    val a_in_16 = Input(Vec(2, UInt(16.W)))
    val b_in_16 = Input(Vec(2, UInt(16.W)))
    val c_in_16 = Input(Vec(2, UInt(16.W)))

    val res_out_32 = Output(UInt(32.W))
    val res_out_16 = Output(Vec(2, UInt(16.W)))
    val valid_out = Output(Bool())
  })

  val fma = Module(new FMA_16_32)
  fma.io.valid_in := io.valid_in
  fma.io.is_bf16 := io.is_bf16
  fma.io.is_fp16 := io.is_fp16
  fma.io.is_fp32 := io.is_fp32
  fma.io.is_widen := io.is_widen

  when(io.is_fp32) {
    fma.io.a_in := io.a_in_32
    fma.io.b_in := io.b_in_32
    fma.io.c_in := io.c_in_32
  }.otherwise {
    fma.io.a_in := Cat(io.a_in_16(1), io.a_in_16(0))
    fma.io.b_in := Cat(io.b_in_16(1), io.b_in_16(0))
    fma.io.c_in := Mux(!io.is_widen, Cat(io.c_in_16(1), io.c_in_16(0)), io.c_in_32)
  }

  io.res_out_32 := fma.io.res_out
  io.res_out_16 := VecInit(fma.io.res_out(15, 0), fma.io.res_out(31, 16))
  io.valid_out := fma.io.valid_out
}

object topMain extends App {
  (new ChiselStage).emitVerilog(new top, args)
}