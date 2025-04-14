package Vreduction

import chisel3._
import chisel3.util._

trait Params {
    val VLEN = 512
    val XLEN = 32
    val exponentWidth : Int = 8
    val significandWidth : Int = 8
    val floatWidth = exponentWidth + significandWidth
}

class VredInput extends Bundle with Params {
  val vlmul         = UInt(3.W)
  val mask          = UInt(VLEN.W)
  val round_mode    = UInt(3.W)
  val fp_format     = UInt(2.W)
  val op_code       = UInt(5.W)
  val is_vec        = Bool()
  val vs1           = UInt(VLEN.W)
  val vs2           = UInt(XLEN.W)
}


class VredOutput extends Bundle with Params{
  val result  = UInt(XLEN.W)
  val fflags  = UInt(5.W)
}


object VfaddOpCode {
  def dummy    = "b11111".U(5.W)
  def fadd     = "b00000".U(5.W)
  def fsub     = "b00001".U(5.W)
  def fmin     = "b00010".U(5.W)
  def fmax     = "b00011".U(5.W)
  def fmerge   = "b00100".U(5.W)
  def fmove    = "b00101".U(5.W)
  def fsgnj    = "b00110".U(5.W)
  def fsgnjn   = "b00111".U(5.W)
  def fsgnjx   = "b01000".U(5.W)
  def feq      = "b01001".U(5.W)
  def fne      = "b01010".U(5.W)
  def flt      = "b01011".U(5.W)
  def fle      = "b01100".U(5.W)
  def fgt      = "b01101".U(5.W)
  def fge      = "b01110".U(5.W)
  def fclass   = "b01111".U(5.W)
  def fmv_f_s  = "b10001".U(5.W)
  def fmv_s_f  = "b10010".U(5.W)
  def fsum_ure = "b11010".U(5.W) // unordered
  def fmin_re  = "b10100".U(5.W)
  def fmax_re  = "b10101".U(5.W)
  def fsum_ore = "b10110".U(5.W) // ordered
  def fminm    = "b11110".U(5.W)
  def fmaxm    = "b10011".U(5.W)
  def fleq     = "b11100".U(5.W)
  def fltq     = "b11011".U(5.W)
}