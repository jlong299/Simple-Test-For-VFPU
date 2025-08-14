/**
  * Integer multiplier to perform:
  *   Two 12*12 UInt multiplications, OR one 24*24 UInt multiplication
  */

package race.vpu.exu.laneexu.fp

import chisel3._
import chisel3.util._
import race.vpu._
import VParams._

//---- Without booth-encoding version ----
class IntMUL_12_24_noBooth extends Module {
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val a_in = Input(UInt(24.W))
    val b_in = Input(UInt(24.W))
    val is_16 = Input(Bool())
    val valid_out = Output(Bool())
    val res_out = Output(UInt(48.W))
  })

  val vs2 = io.a_in
  val vs1 = io.b_in
  val eleWidthOH = Seq(io.is_16, !io.is_16)

  /**
   *  First pipeline stage:
   *    (1) Partial products generation (2) Wallace tree (part)
   */
  val partProd = Wire(Vec(24, UInt(48.W)))
  partProd(0) := Mux(!vs1(0), 0.U, Mux1H(eleWidthOH, Seq(12, 24).map(k => 
                 Cat(0.U((48-k).W), vs2(k-1, 0)))))
  for (i <- 1 until 24) {
    partProd(i) := Mux(!vs1(i), 0.U, Mux1H(eleWidthOH, Seq(12, 24).map(k => 
                 Cat(0.U((48-k-i-i/k*k).W), vs2(i/k * k + k-1, i/k * k), 0.U((i + i/k * k).W)))))
  }

  // Set zero zone to seperate different elements
  val partProdSet0_sew12 = Wire(Vec(24, UInt(48.W)))
  for (i <- 0 until 24) {
    val eIdx12 = i / 12  // element idx
    if (eIdx12 == 0) {partProdSet0_sew12(i) := Cat(0.U(24.W), partProd(i)(23, 0))}
    else {partProdSet0_sew12(i) := Cat(partProd(i)(47, 24), 0.U(24.W))}
  }
  val partProdSet0 = Mux1H(Seq(
    io.is_16 -> partProdSet0_sew12,
    !io.is_16 -> partProd
  ))

  /**
   *  Wallace tree
   */
  // 3-bit full-adder
  def add3_UInt(a: UInt, b: UInt, c: UInt): (UInt, UInt) = {
    val cout = (a & b) | (b & c) | (a & c)
    val sum = a ^ b ^ c
    (cout, sum)
  }
  // ---- Perform wallace reduction once (3 -> 2) ----
  // Input data: n x 48    Output: ([n/3]*2 + n%3) x 48
  def reduce3to2(data: Seq[UInt]): Seq[UInt] = {
    val nAddends = data.size
    val n3Group = nAddends / 3
    val cout = Seq.fill(n3Group)(Wire(UInt(48.W)))
    val sum = Seq.fill(n3Group)(Wire(UInt(48.W)))
    for (i <- 0 until n3Group) {
      val temp_result = add3_UInt(data(3*i), data(3*i+1), data(3*i+2))
      cout(i) := temp_result._1 &
                 Mux1H(Seq(io.is_16  -> "h7fffff7fffff".U(48.W),
                           !io.is_16 -> "hffffffffffff".U(48.W) ))
      sum(i) := temp_result._2
    }
    val cin = cout.map(x => Cat(x(46, 0), 0.U(1.W)))
    sum ++ cin ++ data.drop(3 * n3Group)
  }

  // Generate an array to store number of addends of each wallace stage (e.g., [24, 16, 11, 8, 6, 4, 3])
  def nAddendsSeqGen(num_of_addends: Int): Seq[Int] = {
    num_of_addends match {
      case 3 => Seq(3)
      case n => n +: nAddendsSeqGen((n / 3) * 2 + n % 3)
    }
  }
  val nAddendsSeq = nAddendsSeqGen(24)  // e.g., [24, 16, 11, 8, 6, 4, 3]

  // Perform all wallace stages.
  def wallaceStage(stageIdx: Int): Seq[UInt] = {
    stageIdx match {
      case 0 => reduce3to2(partProd)
      case k => reduce3to2(wallaceStage(k-1))
    }
  }
  val wallaceOut = wallaceStage(nAddendsSeq.size - 1)  // Seq(2)(UInt(48.W))

  /**
   *  Second pipeline stage: 48 + 48
   */
  val is16S1 = RegEnable(io.is_16, io.valid_in)
  val wallaceOutReg = wallaceOut map {x => RegEnable(x, io.valid_in)}

  //---- Sum of final two 48b numbers ----
  // 1. Low 24 bits sum
  val lowSum25 = Wire(UInt(25.W))
  lowSum25 := wallaceOutReg(0)(23, 0) +& wallaceOutReg(1)(23, 0)
  // 2. High 24 bits sum
  val highSum25 = Cat(wallaceOutReg(0)(47, 24), Mux(is16S1, false.B, lowSum25(24))) +
                     Cat(wallaceOutReg(1)(47, 24), Mux(is16S1, false.B, lowSum25(24)))
  val highSum24 = highSum25(24, 1)

  io.valid_out := RegNext(io.valid_in, init = false.B)
  io.res_out := Cat(highSum24, lowSum25(23, 0))
}



//---- Dummy version: only for testing ----
class IntMUL_12_24_dummy extends Module {
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val a_in = Input(UInt(24.W))
    val b_in = Input(UInt(24.W))
    val is_16 = Input(Bool())
    val valid_out = Output(Bool())
    val res_out = Output(UInt(48.W))
  })

  val res_16_high, res_16_low = Wire(UInt(24.W))
  val res_32 = Wire(UInt(48.W))
  res_16_high := io.a_in(23, 12) * io.b_in(23, 12)
  res_16_low := io.a_in(11, 0) * io.b_in(11, 0)
  res_32 := io.a_in * io.b_in

  io.valid_out := RegNext(io.valid_in, init = false.B)


  val res_out = Mux(!io.is_16, res_32, Cat(res_16_high, res_16_low))
  io.res_out := RegEnable(res_out, io.valid_in)
}