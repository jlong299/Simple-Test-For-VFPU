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
    val a_in = Input(UInt(32.W))
    val b_in = Input(UInt(32.W))
    val c_in = Input(UInt(32.W))
    val res_out = Output(UInt(32.W))
    val valid_out = Output(Bool())
  })

  val fma = Module(new FMA_16_32)
  fma.io.valid_in := io.valid_in
  fma.io.is_bf16 := io.is_bf16
  fma.io.is_fp16 := io.is_fp16
  fma.io.is_fp32 := io.is_fp32
  fma.io.is_widen := io.is_widen
  fma.io.a_in := io.a_in
  fma.io.b_in := io.b_in
  fma.io.c_in := io.c_in
  io.res_out := fma.io.res_out
  io.valid_out := fma.io.valid_out
}

object topMain extends App {
  (new ChiselStage).emitVerilog(new top, args)
}