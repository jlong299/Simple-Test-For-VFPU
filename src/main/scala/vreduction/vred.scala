package Vreduction

import chisel3._
import Vreduction._
import chisel3.util._
import chisel3.stage._

class Vfredctrl extends Bundle with Params{
  val fire          = Bool()
  val vlmul         = UInt(3.W)
  val mask          = UInt(VLEN.W)
  val round_mode    = UInt(3.W)
  val fp_format     = UInt(2.W)
  val op_code       = UInt(5.W)
  val is_vec        = Bool()
  val index         = UInt(3.W)
  val vs2           = UInt(32.W)
} 


class Vfreduction extends Module with Params{
  val io = IO(new Bundle {
    val fire = Input(Bool())  
    val index = Input(UInt(3.W))
    val in  = Input(new VredInput)
    val out = Output(new VredOutput)
    val finish = Output(Bool())
  })

  // result format b01->fp16,b10->fp32,b11->fp64
  // pipeline 0 
  val result_pipe0  = Wire(Vec((VLEN/XLEN)/2, UInt(XLEN.W)))
  val fflags_pipe0  = Wire(Vec((VLEN/XLEN)/2, UInt(5.W)))
  val vd_pipe0      = Wire(UInt((VLEN/2).W))
  vd_pipe0 := Cat(result_pipe0.reverse)

  val vctrl_pipe0       = Reg(new Vfredctrl)
  vctrl_pipe0.fire      := io.fire
  vctrl_pipe0.vlmul     := io.in.vlmul     
  vctrl_pipe0.mask      := io.in.mask      
  vctrl_pipe0.round_mode:= io.in.round_mode
  vctrl_pipe0.fp_format := io.in.fp_format 
  vctrl_pipe0.op_code   := io.in.op_code   
  vctrl_pipe0.is_vec    := io.in.is_vec    
  vctrl_pipe0.index     := io.index
  vctrl_pipe0.vs2       := io.in.vs2

  for (i <- 0 until ((VLEN/XLEN)/2)) {

      val fp_a = io.in.vs1(31-1+i*64, 0+i*64)
      val fp_b = io.in.vs1(64-1+i*64, 32+i*64)
      val mask = io.in.mask(i*4+3, i*4)
      val vfred = Module(new VfredFP32WidenMixedFP16_SingleUnit)
      vfred.io.fire := io.fire
      vfred.io.fp_a := fp_a
      vfred.io.fp_b := fp_b
      vfred.io.is_vec := io.in.is_vec
      vfred.io.round_mode := io.in.round_mode
      
      vfred.io.fp_format  := io.in.fp_format  
      vfred.io.mask := mask

      vfred.io.op_code    := io.in.op_code
      vfred.io.fp_aIsFpCanonicalNAN := 0.U
      vfred.io.fp_bIsFpCanonicalNAN := 0.U

      result_pipe0(i) := vfred.io.fp_result
      fflags_pipe0(i) := vfred.io.fflags
  }

  // pipeline 1
  val result_pipe1  = Wire(Vec((VLEN/XLEN)/4, UInt(XLEN.W)))
  val fflags_pipe1  = Wire(Vec((VLEN/XLEN)/4, UInt(5.W)))
  val vd_pipe1      = Wire(UInt((VLEN/4).W))
  vd_pipe1 := Cat(result_pipe1.reverse)

  val vctrl_pipe1       = Reg(new Vfredctrl)
  vctrl_pipe1      := vctrl_pipe0

  for (i <- 0 until ((VLEN/XLEN)/4)) {

      val fp_a = vd_pipe0(31-1+i*64, 0+i*64)
      val fp_b = vd_pipe0(64-1+i*64, 32+i*64)
      val mask = vctrl_pipe0.mask(i*4+3, i*4)
      val vfred = Module(new VfredFP32WidenMixedFP16_SingleUnit)
      vfred.io.fire := vctrl_pipe0.fire
      vfred.io.fp_a := fp_a
      vfred.io.fp_b := fp_b
      vfred.io.is_vec := vctrl_pipe0.is_vec
      vfred.io.round_mode := vctrl_pipe0.round_mode
      
      vfred.io.fp_format  := vctrl_pipe0.fp_format  
      vfred.io.mask := mask

      vfred.io.op_code    := vctrl_pipe0.op_code
      vfred.io.fp_aIsFpCanonicalNAN := 0.U
      vfred.io.fp_bIsFpCanonicalNAN := 0.U

      result_pipe1(i) := vfred.io.fp_result
      fflags_pipe1(i) := vfred.io.fflags
  }

  // pipeline 2
  val result_pipe2  = Wire(Vec((VLEN/XLEN)/8, UInt(XLEN.W)))
  val fflags_pipe2  = Wire(Vec((VLEN/XLEN)/8, UInt(5.W)))
  val vd_pipe2      = Wire(UInt((VLEN/8).W))
  vd_pipe2 := Cat(result_pipe2.reverse)

  val vctrl_pipe2       = Reg(new Vfredctrl)
  vctrl_pipe2      := vctrl_pipe1

  for (i <- 0 until ((VLEN/XLEN)/8)) {

      val fp_a = vd_pipe1(31-1+i*64, 0+i*64)
      val fp_b = vd_pipe1(64-1+i*64, 32+i*64)
      val mask = vctrl_pipe1.mask(i*4+3, i*4)
      val vfred = Module(new VfredFP32WidenMixedFP16_SingleUnit)
      vfred.io.fire := vctrl_pipe1.fire
      vfred.io.fp_a := fp_a
      vfred.io.fp_b := fp_b
      vfred.io.is_vec := vctrl_pipe1.is_vec
      vfred.io.round_mode := vctrl_pipe1.round_mode
      
      vfred.io.fp_format  := vctrl_pipe1.fp_format  
      vfred.io.mask := mask

      vfred.io.op_code    := vctrl_pipe1.op_code
      vfred.io.fp_aIsFpCanonicalNAN := 0.U
      vfred.io.fp_bIsFpCanonicalNAN := 0.U

      result_pipe2(i) := vfred.io.fp_result
      fflags_pipe2(i) := vfred.io.fflags
  }

  // pipeline 3
  val result_pipe3  = Wire(Vec((VLEN/XLEN)/16, UInt(XLEN.W)))
  val fflags_pipe3  = Wire(Vec((VLEN/XLEN)/16, UInt(5.W)))
  val vd_pipe3      = Wire(UInt((VLEN/16).W))
  vd_pipe3 := Cat(result_pipe3.reverse)

  val vctrl_pipe3       = Reg(new Vfredctrl)
  vctrl_pipe3      := vctrl_pipe2

  for (i <- 0 until ((VLEN/XLEN)/16)) {

      val fp_a = vd_pipe2(31-1+i*64, 0+i*64)
      val fp_b = vd_pipe2(64-1+i*64, 32+i*64)
      val mask = vctrl_pipe2.mask(i*4+3, i*4)
      val vfred = Module(new VfredFP32WidenMixedFP16_SingleUnit)
      vfred.io.fire := vctrl_pipe2.fire
      vfred.io.fp_a := fp_a
      vfred.io.fp_b := fp_b
      vfred.io.is_vec := vctrl_pipe2.is_vec
      vfred.io.round_mode := vctrl_pipe2.round_mode
      
      vfred.io.fp_format  := vctrl_pipe2.fp_format  
      vfred.io.mask := mask

      vfred.io.op_code    := vctrl_pipe2.op_code
      vfred.io.fp_aIsFpCanonicalNAN := 0.U
      vfred.io.fp_bIsFpCanonicalNAN := 0.U

      result_pipe3(i) := vfred.io.fp_result
      fflags_pipe3(i) := vfred.io.fflags
  }

  // pipeline 4
  val result_pipe4  = Wire(Vec((VLEN/XLEN)/32, UInt(XLEN.W)))
  val fflags_pipe4  = Wire(Vec((VLEN/XLEN)/32, UInt(5.W)))
  val vd_pipe4      = Wire(UInt((VLEN/32).W))
  vd_pipe4 := Cat(result_pipe4.reverse)

  val vctrl_pipe4       = Reg(new Vfredctrl)
  vctrl_pipe4      := vctrl_pipe3

  for (i <- 0 until ((VLEN/XLEN)/32)) {

      val fp_a = vd_pipe3(31-1+i*64, 0+i*64)
      val fp_b = vd_pipe3(64-1+i*64, 32+i*64)
      val mask = vctrl_pipe3.mask(i*4+3, i*4)
      val vfred = Module(new VfredFP32WidenMixedFP16_SingleUnit)
      vfred.io.fire := vctrl_pipe3.fire
      vfred.io.fp_a := fp_a
      vfred.io.fp_b := fp_b
      vfred.io.is_vec := vctrl_pipe3.is_vec
      vfred.io.round_mode := vctrl_pipe3.round_mode
      
      vfred.io.fp_format  := vctrl_pipe3.fp_format  
      vfred.io.mask := mask

      vfred.io.op_code    := vctrl_pipe3.op_code
      vfred.io.fp_aIsFpCanonicalNAN := 0.U
      vfred.io.fp_bIsFpCanonicalNAN := 0.U

      result_pipe4(i) := vfred.io.fp_result
      fflags_pipe4(i) := vfred.io.fflags
  }

  // pipeline 5
  // vlen = 2048
  // vlen/32 = 64 = 2^6

  val result_pipe5  = Wire(Vec((VLEN/XLEN)/64, UInt(XLEN.W)))
  val fflags_pipe5  = Wire(Vec((VLEN/XLEN)/64, UInt(5.W)))
  val vd_pipe5      = Wire(UInt((VLEN/64).W))
  vd_pipe5 := Cat(result_pipe5.reverse)

  val vctrl_pipe5       = Reg(new Vfredctrl)
  vctrl_pipe5      := vctrl_pipe4   

  for (i <- 0 until ((VLEN/XLEN)/64)) {

      val fp_a = vd_pipe4(31-1+i*64, 0+i*64)
      val fp_b = vd_pipe4(64-1+i*64, 32+i*64)
      val mask = vctrl_pipe4.mask(i*4+3, i*4)
      val vfred = Module(new VfredFP32WidenMixedFP16_SingleUnit)
      vfred.io.fire := vctrl_pipe4.fire
      vfred.io.fp_a := fp_a
      vfred.io.fp_b := fp_b
      vfred.io.is_vec := vctrl_pipe4.is_vec
      vfred.io.round_mode := vctrl_pipe4.round_mode
      
      
      vfred.io.fp_format  := vctrl_pipe4.fp_format  
      vfred.io.mask := mask

      vfred.io.op_code    := vctrl_pipe4.op_code
      vfred.io.fp_aIsFpCanonicalNAN := 0.U
      vfred.io.fp_bIsFpCanonicalNAN := 0.U

      result_pipe5(i) := vfred.io.fp_result
      fflags_pipe5(i) := vfred.io.fflags
  }


  // pipeline 6
  // final stage
  // with one reg for accumulating the result
  val vctrl_pipe6       = Reg(new Vfredctrl)
  vctrl_pipe6      := vctrl_pipe5

  val vfred_pipe6 = Module(new VfredFP32WidenMixedFP16_SingleUnit)
  val fp32_result = vfred_pipe6.io.fp_result
  val fp32_fflags = vfred_pipe6.io.fflags
  
  vfred_pipe6.io.fire := vctrl_pipe5.fire
  vfred_pipe6.io.fp_a := Mux(vctrl_pipe5.index =/= 0.U, fp32_result, vctrl_pipe5.vs2)
  vfred_pipe6.io.fp_b := vd_pipe5
  vfred_pipe6.io.is_vec := vctrl_pipe5.is_vec
  vfred_pipe6.io.round_mode := vctrl_pipe5.round_mode
  
  
  vfred_pipe6.io.fp_format  := vctrl_pipe5.fp_format  
  vfred_pipe6.io.mask := 0.U

  vfred_pipe6.io.op_code    := vctrl_pipe5.op_code
  vfred_pipe6.io.fp_aIsFpCanonicalNAN := 0.U
  vfred_pipe6.io.fp_bIsFpCanonicalNAN := 0.U


  val vlmul_pipe6_cvt = MuxLookup(vctrl_pipe6.vlmul, "b000".U, Seq(
    "b000".U -> "b000".U,   // LMUL = 1 → 000
    "b001".U -> "b001".U,   // LMUL = 2 → 001
    "b010".U -> "b011".U,   // LMUL = 4 → 011
    "b011".U -> "b111".U    // LMUL = 8 → 111
  ))

  // pipeline 7
  // if fp16, need another stage to caculate
  val vctrl_pipe7_fp16  = Reg(new Vfredctrl)
  vctrl_pipe7_fp16      := vctrl_pipe6

  val vfred_pipe7_fp16 = Module(new FloatAdderF16Pipeline)
  val fp16_result = vfred_pipe7_fp16.io.fp_c
  val fp16_fflags = vfred_pipe7_fp16.io.fflags
  
  vfred_pipe7_fp16.io.fire := vctrl_pipe6.fire
  vfred_pipe7_fp16.io.fp_a := fp32_result(31,16)
  vfred_pipe7_fp16.io.fp_b := fp32_result(15,0)
  vfred_pipe7_fp16.io.is_sub := vctrl_pipe6.op_code(0)
  vfred_pipe7_fp16.io.round_mode := vctrl_pipe6.round_mode
  vfred_pipe7_fp16.io.mask := 0.U
  vfred_pipe7_fp16.io.op_code    := vctrl_pipe6.op_code
  vfred_pipe7_fp16.io.fp_aIsFpCanonicalNAN := 0.U
  vfred_pipe7_fp16.io.fp_bIsFpCanonicalNAN := 0.U

  val vctrl_pipe7_fp16_cvt = MuxLookup(vctrl_pipe7_fp16.vlmul, "b000".U, Seq(
    "b000".U -> "b000".U,   // LMUL = 1 → 000
    "b001".U -> "b001".U,   // LMUL = 2 → 001
    "b010".U -> "b011".U,   // LMUL = 4 → 011
    "b011".U -> "b111".U    // LMUL = 8 → 111
  ))

  // arbitrate the result
  val fp32_finish = (vctrl_pipe6.index === vlmul_pipe6_cvt) & vctrl_pipe6.fire
  val is_fp32 = vctrl_pipe6.fp_format === "b10".U
  val fp16_finish = (vctrl_pipe7_fp16.index === vctrl_pipe7_fp16_cvt) & vctrl_pipe7_fp16.fire
  val is_fp16 = vctrl_pipe7_fp16.fp_format === "b01".U

  io.out.result := Mux(is_fp32 & fp32_finish, fp32_result, 
  Mux(is_fp16 & fp16_finish, Cat(Fill(16, 0.U), fp16_result), 0.U))

  io.out.fflags := Mux(is_fp32 & fp32_finish, fp32_fflags, 
  Mux(is_fp16 & fp16_finish, fp16_fflags, 0.U))

  io.finish := (is_fp32 & fp32_finish) | (is_fp16 & fp16_finish)
}

class VfredFP32WidenMixedFP16_SingleUnit() extends Module {
    val exponentWidth = 8
    val significandWidth = 24
    val floatWidth = exponentWidth + significandWidth
    val io = IO(new Bundle() {
    val fire          = Input (Bool())
    val fp_a, fp_b    = Input (UInt(floatWidth.W)) // fp_a -> vs2, fp_b -> vs1

    val mask          = Input (UInt(4.W))
    val is_vec        = Input (Bool())
    val round_mode    = Input (UInt(3.W))
    val fp_format     = Input (UInt(2.W)) // result format b01->fp16,b10->fp32,b11->fp64

    val op_code       = Input (UInt(5.W)) // TODO: op_code width
    val fp_aIsFpCanonicalNAN = Input (Bool())
    val fp_bIsFpCanonicalNAN = Input (Bool())

    val fp_result     = Output(UInt(floatWidth.W))
    val fflags        = Output(UInt(5.W))
  })

  val fire = io.fire
  val is_vec_reg    = RegEnable(io.is_vec, fire)
  val fp_format_reg = RegEnable(io.fp_format, fire)

  val fast_is_sub = io.op_code(0)
  val res_is_f16 = fp_format_reg === 1.U
  val res_is_f32 = fp_format_reg === 2.U

 
  
  val hasMinMaxCompare = true
  val fp_format = io.fp_format-1.U


  val U_F16 = Module(new FloatAdderF16Pipeline(is_print = false,hasMinMaxCompare = hasMinMaxCompare))
  U_F16.io.fire := fire
  U_F16.io.fp_a := io.fp_a(31,16)
  U_F16.io.fp_b := io.fp_b(31,16)
  U_F16.io.is_sub := fast_is_sub
  U_F16.io.round_mode := io.round_mode
  U_F16.io.mask := 0.U
  U_F16.io.op_code    := io.op_code
  U_F16.io.fp_aIsFpCanonicalNAN := io.fp_aIsFpCanonicalNAN
  U_F16.io.fp_bIsFpCanonicalNAN := io.fp_bIsFpCanonicalNAN

  val U_F16_1_result = U_F16.io.fp_c
  val U_F16_1_fflags = U_F16.io.fflags


  val U_F32_Mixed = Module(new FloatAdderF32WidenF16MixedPipeline(is_print = false,hasMinMaxCompare = hasMinMaxCompare))
  U_F32_Mixed.io.fire := fire
  U_F32_Mixed.io.fp_a := io.fp_a
  U_F32_Mixed.io.fp_b := io.fp_b

  U_F32_Mixed.io.is_sub       := 0.U
  U_F32_Mixed.io.round_mode   := io.round_mode
  // U_F32_Mixed.io.mask         := io.mask(0)
  // TODO:
  U_F32_Mixed.io.fp_format    := fp_format
  U_F32_Mixed.io.op_code      := io.op_code
  U_F32_Mixed.io.fp_aIsFpCanonicalNAN := io.fp_aIsFpCanonicalNAN
  U_F32_Mixed.io.fp_bIsFpCanonicalNAN := io.fp_bIsFpCanonicalNAN

  val U_F32_result = U_F32_Mixed.io.fp_c
  val U_F32_fflags = U_F32_Mixed.io.fflags
  val U_F16_0_result = U_F32_Mixed.io.fp_c(15,0)
  val U_F16_0_fflags = U_F32_Mixed.io.fflags

  val fp_f32_result = U_F32_result
  val fp_f16_result = Cat(Fill(16, is_vec_reg) & U_F16_1_result, U_F16_0_result)

  io.fp_result := Mux1H(
    Seq(
      res_is_f16,
      res_is_f32
    ),
    Seq(
      fp_f16_result,
      fp_f32_result
    )
  )

  io.fflags := Mux(res_is_f32, U_F32_fflags, (U_F16_1_fflags | U_F16_0_fflags))
}


class Adder(val aw:Int,val bw:Int,val cw:Int, val is_sub:Boolean = false) extends Module {
  val io = IO(new Bundle() {
    val a = Input (UInt(aw.W))
    val b = Input (UInt(bw.W))
    val c = Output(UInt(cw.W))
  })
  if (cw > aw) {
    io.c := (if (is_sub) io.a -& io.b else io.a +& io.b)
  }
  else {
    io.c := (if (is_sub) io.a - io.b else io.a + io.b)
  }
}

class FarPathF32WidenF16MixedAdderPipeline(val AW:Int, val AdderType:String, val stage0AdderWidth: Int = 0) extends Module {
  val io = IO(new Bundle() {
    val fire= Input (Bool())
    val A   = Input (UInt(AW.W))
    val B   = Input (UInt(AW.W))
    val result = Output(UInt(AW.W))
  })
  val fire = io.fire
  if (stage0AdderWidth == 0) io.result  := RegEnable(io.A, fire) + RegEnable(io.B, fire)
  else if (stage0AdderWidth > 0) {
    val short_adder_num = AW/stage0AdderWidth + 1
    val seq_short_full_adder = (for (i <- 0 until short_adder_num) yield {
      if (i == (short_adder_num - 1)) io.A(AW-1,stage0AdderWidth*i) + io.B(AW-1,stage0AdderWidth*i)
      else io.A(stage0AdderWidth*(i+1)-1,stage0AdderWidth*i) +& io.B(stage0AdderWidth*(i+1)-1,stage0AdderWidth*i)
    }).reverse.reduce(Cat(_,_))
    val reg_short_adder_sum = (for (i <- 0 until short_adder_num) yield {
      if (i == (short_adder_num - 1)) seq_short_full_adder(seq_short_full_adder.getWidth-1,(stage0AdderWidth+1)*i)
      else seq_short_full_adder((stage0AdderWidth+1)*i+stage0AdderWidth-1,(stage0AdderWidth+1)*i)
    }).reverse.reduce(Cat(_,_))
    val reg_short_adder_car = (for (i <- 0 until short_adder_num) yield {
      if (i == (short_adder_num - 1)) 0.U((AW-stage0AdderWidth*i-1).W)
      else Cat(seq_short_full_adder((stage0AdderWidth+1)*(i+1)-1),0.U((stage0AdderWidth-1).W))
    }).reverse.reduce(Cat(_,_))
    io.result := Cat(
      RegEnable(reg_short_adder_sum(AW-1,stage0AdderWidth), fire) + RegEnable(Cat(reg_short_adder_car,0.U)(AW-1,stage0AdderWidth), fire),
      RegEnable(reg_short_adder_sum(stage0AdderWidth-1,0), fire)
    )
  }
}

class FarShiftRightWithMuxInvFirst(val srcW:Int,shiftValueW:Int) extends Module {
  val io = IO(new Bundle() {
    val src        = Input (UInt(srcW.W))
    val shiftValue = Input (UInt(shiftValueW.W))
    val result     = Output(UInt())
    val EOP        = Input(Bool())
  })
  def shiftRightWithMuxInvFirst(srcValue: UInt, shiftValue: UInt, EOP:Bool): UInt = {
    val vecLength  = shiftValue.getWidth + 1
    val res_vec    = Wire(Vec(vecLength,UInt(srcValue.getWidth.W)))
    res_vec(0)    := srcValue
    for (i <- 0 until shiftValue.getWidth) {
      res_vec(i+1) := Mux(shiftValue(i), Cat(Fill(1<<i,EOP),res_vec(i) >> (1<<i)), res_vec(i))
    }
    res_vec(vecLength-1)
  }
  io.result := shiftRightWithMuxInvFirst(io.src,io.shiftValue,io.EOP)
}

class FarPathF32WidenF16MixedPipeline(
                                                     exponentWidth : Int = 8,
                                                     significandWidth : Int = 24,
                                                     val is_print:Boolean = false,
                                                     val hasMinMaxCompare:Boolean = false
                                                   ) extends Module {
  val floatWidth = exponentWidth + significandWidth
  val io = IO(new Bundle() {
    val fire        = Input (Bool())
    val fp_a, fp_b  = Input (UInt(floatWidth.W))
    val fp_c        = Output(UInt(floatWidth.W))
    val is_sub      = Input (Bool())
    val round_mode  = Input (UInt(3.W))
    val fflags      = Output(UInt(5.W))
    val absEaSubEb  = Output(UInt(exponentWidth.W))
    val res_is_f32  = Input (Bool())
    val isEfp_bGreater = if (hasMinMaxCompare) Output(UInt(1.W)) else Output(UInt(0.W))
  })
  val fire = io.fire
  val res_is_f32 = io.res_is_f32
  val res_is_f32_reg = RegEnable(res_is_f32, fire)
  val fp_a_sign = io.fp_a.head(1).asBool
  val fp_b_sign = io.fp_b.head(1).asBool
  val efficient_fp_b_sign = (fp_b_sign ^ io.is_sub).asBool
  val EOP = ( fp_a_sign ^ efficient_fp_b_sign ).asBool
  val RNE_reg = RegEnable(io.round_mode === "b000".U, fire)  //Round to Nearest, ties to Even
  val RTZ_reg = RegEnable(io.round_mode === "b001".U, fire)  //Round towards Zero
  val RDN_reg = RegEnable(io.round_mode === "b010".U, fire)  //Round Down (towards −∞)
  val RUP_reg = RegEnable(io.round_mode === "b011".U, fire)  //Round Up (towards +∞)
  val RMM_reg = RegEnable(io.round_mode === "b100".U, fire)  //Round to Nearest, ties to Max Magnitude

  val NV  = WireInit(false.B)  //Invalid Operation
  val DZ  = WireInit(false.B)  //Divide by Zero
  val OF_reg  = WireInit(false.B)  //Overflow
  val UF  = WireInit(false.B)  //Underflow
  val NX  = WireInit(false.B)  //Inexact

  io.fflags := Cat(NV,DZ,OF_reg,UF,NX)
  val fp_a_mantissa            = io.fp_a.tail(1 + exponentWidth)
  val fp_b_mantissa            = io.fp_b.tail(1 + exponentWidth)
  val fp_a_mantissa_isnot_zero = io.fp_a.tail(1 + exponentWidth).orR
  val fp_b_mantissa_isnot_zero = io.fp_b.tail(1 + exponentWidth).orR
  val Efp_a = io.fp_a(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_b = io.fp_b(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_a_is_not_zero  = Efp_a.orR
  val Efp_b_is_not_zero  = Efp_b.orR
  val Efp_a_is_greater_than_1= Efp_a.head(exponentWidth-1).orR
  val Efp_b_is_greater_than_1= Efp_b.head(exponentWidth-1).orR
  val Efp_a_is_all_one   = Efp_a.andR
  val Efp_b_is_all_one   = Efp_b.andR

  val U_Efp_aSubEfp_b = Module(new Adder(Efp_a.getWidth,Efp_a.getWidth,Efp_a.getWidth,is_sub = true))
  U_Efp_aSubEfp_b.io.a := Efp_a | !Efp_a_is_not_zero
  U_Efp_aSubEfp_b.io.b := Efp_b | !Efp_b_is_not_zero
  val Efp_aSubEfp_b     = U_Efp_aSubEfp_b.io.c
  val U_Efp_bSubEfp_a = Module(new Adder(Efp_a.getWidth,Efp_a.getWidth,Efp_a.getWidth,is_sub = true))
  U_Efp_bSubEfp_a.io.a := Efp_b | !Efp_b_is_not_zero
  U_Efp_bSubEfp_a.io.b := Efp_a | !Efp_a_is_not_zero
  val Efp_bSubEfp_a     = U_Efp_bSubEfp_a.io.c
  val isEfp_bGreater    = Efp_b > Efp_a
  val Efp_a_sub_1       = Mux(Efp_a_is_not_zero,Efp_a - 1.U,0.U)
  val Efp_b_sub_1       = Mux(Efp_b_is_not_zero,Efp_b - 1.U,0.U)
  val absEaSubEb        = Wire(UInt(Efp_a.getWidth.W))

  io.absEaSubEb := absEaSubEb
  absEaSubEb := Mux(isEfp_bGreater, Efp_bSubEfp_a, Efp_aSubEfp_b)
  
  val significand_fp_a   = Cat(Efp_a_is_not_zero,fp_a_mantissa)
  val significand_fp_b   = Cat(Efp_b_is_not_zero,fp_b_mantissa)
  val E_greater          = Mux(isEfp_bGreater, Efp_b, Efp_a)
  val EA                 = Mux(EOP, E_greater-1.U, E_greater)
  val EA_add1            = EA + 1.U
  val greaterSignificand = Mux(isEfp_bGreater, significand_fp_b, significand_fp_a)
  val smallerSignificand = Mux(isEfp_bGreater, significand_fp_a, significand_fp_b)
  val farmaxShiftValue   = (significandWidth+2).U
  val A                  = Mux(EOP,Cat(greaterSignificand,0.U),Cat(0.U,greaterSignificand))

  val widenWidth = significandWidth + 3
  val fp_b_mantissa_widen = Mux(EOP,Cat(~significand_fp_b,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_b,0.U(widenWidth.W)))
  val U_far_rshift_1 = Module(new FarShiftRightWithMuxInvFirst(fp_b_mantissa_widen.getWidth,farmaxShiftValue.getWidth))
  U_far_rshift_1.io.src := fp_b_mantissa_widen
  U_far_rshift_1.io.shiftValue := Efp_aSubEfp_b.asTypeOf(farmaxShiftValue)
  U_far_rshift_1.io.EOP := EOP
  val U_far_rshift_fp_b_result = U_far_rshift_1.io.result

  val fp_a_mantissa_widen = Mux(EOP,Cat(~significand_fp_a,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_a,0.U(widenWidth.W)))
  val U_far_rshift_2 = Module(new FarShiftRightWithMuxInvFirst(fp_a_mantissa_widen.getWidth,farmaxShiftValue.getWidth))
  U_far_rshift_2.io.src := fp_a_mantissa_widen
  U_far_rshift_2.io.shiftValue := Efp_bSubEfp_a.asTypeOf(farmaxShiftValue)
  U_far_rshift_2.io.EOP := EOP
  val U_far_rshift_fp_a_result = U_far_rshift_2.io.result

  val far_rshift_widen_result = Mux(isEfp_bGreater,U_far_rshift_fp_a_result,U_far_rshift_fp_b_result)
  val absEaSubEb_is_greater = Mux(res_is_f32,absEaSubEb > (significandWidth + 3).U,absEaSubEb > (11 + 3).U)
  val B = Mux(absEaSubEb_is_greater,Fill(significandWidth+1,EOP),far_rshift_widen_result.head(significandWidth+1))
  val B_guard_normal     = Mux(
    absEaSubEb_is_greater,
    false.B,
    Mux(
      EOP,
      Mux(res_is_f32,!far_rshift_widen_result.head(significandWidth+2)(0).asBool,!far_rshift_widen_result.head(11+2)(0).asBool),
      Mux(res_is_f32,far_rshift_widen_result.head(significandWidth+2)(0).asBool,far_rshift_widen_result.head(11+2)(0).asBool)
    )
  )
  val B_round_normal     = Mux(
    absEaSubEb_is_greater,
    false.B,
    Mux(
      EOP,
      Mux(res_is_f32,!far_rshift_widen_result.head(significandWidth+3)(0).asBool,!far_rshift_widen_result.head(11+3)(0).asBool),
      Mux(res_is_f32,far_rshift_widen_result.head(significandWidth+3)(0).asBool,far_rshift_widen_result.head(11+3)(0).asBool)
    )
  )
  val B_sticky_normal    = Mux(
    absEaSubEb_is_greater,
    smallerSignificand.orR,
    Mux(
      EOP,
      Mux(res_is_f32,(~far_rshift_widen_result.tail(significandWidth+3)).asUInt.orR,(~far_rshift_widen_result.tail(11+3)).asUInt.orR),
      Mux(res_is_f32,far_rshift_widen_result.tail(significandWidth+3).orR,far_rshift_widen_result.tail(11+3).orR)
    )
  )
  val B_rsticky_normal   = B_round_normal | B_sticky_normal
  val B_guard_overflow   = Mux(
    absEaSubEb_is_greater,
    false.B,
    Mux(
      EOP,
      Mux(res_is_f32,!far_rshift_widen_result.head(significandWidth+1)(0).asBool,!far_rshift_widen_result.head(11+1)(0).asBool),
      Mux(res_is_f32,far_rshift_widen_result.head(significandWidth+1)(0).asBool,far_rshift_widen_result.head(11+1)(0).asBool)
    )
  )
  val B_round_overflow   = B_guard_normal
  val B_sticky_overflow  = B_round_normal | B_sticky_normal
  val B_rsticky_overflow = B_round_overflow | B_sticky_overflow
  val U_FS0 = Module(new FarPathF32WidenF16MixedAdderPipeline(AW = A.getWidth, AdderType = "FS0", stage0AdderWidth = 18))
  U_FS0.io.fire := fire
  U_FS0.io.A := A
  U_FS0.io.B := B
  val FS0_reg = U_FS0.io.result
  val U_FS1 = Module(new FarPathF32WidenF16MixedAdderPipeline(AW = A.getWidth, AdderType = "FS1", stage0AdderWidth = 18))
  U_FS1.io.fire := fire
  U_FS1.io.A := A + Mux(res_is_f32,2.U,2.U<<13)
  U_FS1.io.B := B
  val FS1_reg = U_FS1.io.result
  val FS0_0_reg = Mux(res_is_f32_reg,FS0_reg(0),FS0_reg(13))
  val FS0_1_reg = Mux(res_is_f32_reg,FS0_reg(1),FS0_reg(14))
  val far_case_normal_reg   = !FS0_reg.head(1).asBool
  val far_case_overflow_reg = FS0_reg.head(1).asBool
  val lgs_normal_reg = Cat(FS0_0_reg,
    RegEnable(
      Mux(
        EOP,
        (~Cat(B_guard_normal,B_rsticky_normal)).asUInt+1.U,
        Cat(B_guard_normal,B_rsticky_normal)
      ), fire
    )
  )

  val far_sign_result_reg = RegEnable(Mux(isEfp_bGreater, efficient_fp_b_sign, fp_a_sign), fire)
  val far_case_normal_round_up_reg = (RegEnable(EOP, fire) & !lgs_normal_reg(1) & !lgs_normal_reg(0)) |
    (RNE_reg & lgs_normal_reg(1) & (lgs_normal_reg(2) | lgs_normal_reg(0))) |
    (RDN_reg & far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
    (RUP_reg & !far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
    (RMM_reg & lgs_normal_reg(1))
  val normal_fsel0_reg = (!FS0_0_reg & far_case_normal_round_up_reg) | !far_case_normal_round_up_reg
  val normal_fsel1_reg = FS0_0_reg & far_case_normal_round_up_reg
  val A_0 = Mux(res_is_f32,A(0),A(13))
  val B_0 = Mux(res_is_f32,B(0),B(13))
  val grs_overflow_reg = RegEnable(Mux(EOP,Cat(A_0,0.U,0.U) - Cat(~B_0,B_guard_normal,B_rsticky_normal),Cat(A_0 ^ B_0,B_guard_normal,B_rsticky_normal)), fire)
  val lgs_overflow_reg = Cat(FS0_1_reg,grs_overflow_reg(2),grs_overflow_reg(1) | grs_overflow_reg(0))
  val far_case_overflow_round_up_reg = (RegEnable(EOP, fire) & !lgs_overflow_reg(1) & !lgs_overflow_reg(0)) |
    (RNE_reg & lgs_overflow_reg(1) & (lgs_overflow_reg(2) | lgs_overflow_reg(0))) |
    (RDN_reg & far_sign_result_reg & (lgs_overflow_reg(1) | lgs_overflow_reg(0))) |
    (RUP_reg & !far_sign_result_reg & (lgs_overflow_reg(1) | lgs_overflow_reg(0))) |
    (RMM_reg & lgs_overflow_reg(1))
  val overflow_fsel0_reg = (!FS0_1_reg & far_case_overflow_round_up_reg) | !far_case_overflow_round_up_reg
  val overflow_fsel1_reg =  FS0_1_reg & far_case_overflow_round_up_reg
  val far_exponent_result_reg = Wire(UInt(exponentWidth.W))
  val far_fraction_result_reg = Wire(UInt((significandWidth-1).W))
  far_exponent_result_reg := Mux(
    far_case_overflow_reg | (FS1_reg.head(1).asBool & FS0_0_reg & far_case_normal_round_up_reg) | (RegEnable(!EA.orR, fire) & FS0_reg.tail(1).head(1).asBool),
    RegEnable(EA_add1, fire),
    RegEnable(EA, fire)
  )
  OF_reg := RegEnable(Mux(res_is_f32,EA_add1.andR,EA_add1.tail(3).andR), fire) & (far_case_overflow_reg | (FS1_reg.head(1).asBool & FS0_0_reg & far_case_normal_round_up_reg))
  NX := Mux(far_case_normal_reg,lgs_normal_reg(1,0).orR,lgs_overflow_reg(1,0).orR) | OF_reg
  far_fraction_result_reg := Mux1H(
    Seq(
      far_case_normal_reg & normal_fsel0_reg,
      far_case_normal_reg & normal_fsel1_reg,
      far_case_overflow_reg & overflow_fsel0_reg,
      far_case_overflow_reg & overflow_fsel1_reg
    ),
    Seq(
      Cat(FS0_reg(significandWidth-2,1+13), Mux(res_is_f32_reg,FS0_reg(13),FS0_reg(13) ^ far_case_normal_round_up_reg), FS0_reg(12,1),FS0_0_reg ^ far_case_normal_round_up_reg),
      Cat(FS1_reg(significandWidth-2,1+13), Mux(res_is_f32_reg,FS1_reg(13),0.U), FS1_reg(12,1),0.U),
      Cat(FS0_reg(significandWidth-1,2+13), Mux(res_is_f32_reg,FS0_reg(14),FS0_reg(14) ^ far_case_overflow_round_up_reg), FS0_reg(13,2),FS0_1_reg ^ far_case_overflow_round_up_reg),
      FS1_reg(significandWidth-1,1)
    )
  )
  val result_overflow_reg = Mux(
    RTZ_reg | (RDN_reg & !far_sign_result_reg) | (RUP_reg & far_sign_result_reg),
    Cat(far_sign_result_reg,Fill(exponentWidth-1,1.U),0.U,Fill(significandWidth-1,1.U)),
    Cat(far_sign_result_reg,Fill(exponentWidth,1.U),Fill(significandWidth-1,0.U))
  )
  io.fp_c := Mux(OF_reg,
    result_overflow_reg,
    Cat(far_sign_result_reg,far_exponent_result_reg,far_fraction_result_reg)
  )
  if (hasMinMaxCompare) io.isEfp_bGreater := isEfp_bGreater
  else io.isEfp_bGreater := 0.U
}

class ClosePathF32WidenF16MixedPipelineAdder(val adderWidth:Int, val adderType:String) extends Module {
  val io = IO(new Bundle() {
    val adder_op0    = Input(UInt(adderWidth.W))
    val adder_op1    = Input(UInt(adderWidth.W))
    val result       = Output(UInt((adderWidth+1).W))
  })
  if (adderType == "CS0" | adderType == "CS1") {
    io.result  :=  Cat(0.U,io.adder_op0) - Cat(0.U,io.adder_op1)
  }
  if (adderType == "CS2" | adderType == "CS3") {
    io.result  :=  Cat(io.adder_op0,0.U) - Cat(0.U,io.adder_op1)
  }
}

class CloseShiftLeftWithMux(val srcW:Int,shiftValueW:Int) extends Module {
  val io = IO(new Bundle() {
    val src        = Input (UInt(srcW.W))
    val shiftValue = Input (UInt(shiftValueW.W))
    val result     = Output(UInt())
  })
  def shiftLeftWithMux(srcValue: UInt, shiftValue: UInt): UInt = {
    val vecLength  = shiftValue.getWidth + 1
    val res_vec    = Wire(Vec(vecLength,UInt(srcValue.getWidth.W)))
    res_vec(0)    := srcValue
    for (i <- 0 until shiftValue.getWidth) {
      res_vec(i+1) := Mux(shiftValue(shiftValue.getWidth-1-i), res_vec(i) << (1<<(shiftValue.getWidth-1-i)), res_vec(i))
    }
    res_vec(vecLength-1)
  }
  io.result := shiftLeftWithMux(io.src,io.shiftValue)
}

object LZD {
  def apply(in: UInt): UInt = PriorityEncoder(Reverse(Cat(in, 1.U)))
}

class ClosePathF32WidenF16MixedPipeline(
                                                       exponentWidth : Int = 8,
                                                       var significandWidth : Int = 24,
                                                       val is_print:Boolean = false,
                                                       val hasMinMaxCompare:Boolean = false
                                                     ) extends Module {
  val floatWidth = exponentWidth + significandWidth
  val io = IO(new Bundle() {
    val fire        = Input (Bool())
    val fp_a, fp_b  = Input (UInt(floatWidth.W))
    val fp_c        = Output(UInt(floatWidth.W))
    val round_mode  = Input (UInt(3.W))
    val fflags      = Output(UInt(5.W))
    val res_is_f32  = Input (Bool())
    val CS1         = if (hasMinMaxCompare) Output(UInt((significandWidth+1).W)) else Output(UInt(0.W))
  })
  val fire = io.fire
  val res_is_f32 = io.res_is_f32
  val fp_a_sign = io.fp_a.head(1).asBool
  val fp_b_sign = io.fp_b.head(1).asBool
  val RNE = io.round_mode === "b000".U
  val RTZ = io.round_mode === "b001".U
  val RDN = io.round_mode === "b010".U
  val RUP = io.round_mode === "b011".U
  val RMM = io.round_mode === "b100".U
  val NV = WireInit(false.B)
  val DZ = WireInit(false.B)
  val OF = WireInit(false.B)
  val UF = WireInit(false.B)
  val NX_reg = WireInit(false.B)
  io.fflags := Cat(NV,DZ,OF,UF,NX_reg)
  val fp_a_mantissa = io.fp_a.tail(1 + exponentWidth)
  val fp_b_mantissa = io.fp_b.tail(1 + exponentWidth)
  val Efp_a = io.fp_a(floatWidth - 2, floatWidth - 1 - exponentWidth)
  val Efp_b = io.fp_b(floatWidth - 2, floatWidth - 1 - exponentWidth)
  val Efp_a_is_not_zero = Efp_a.orR
  val Efp_b_is_not_zero = Efp_b.orR
  val Efp_b_is_greater = (Efp_b(0) ^ Efp_a(0)) & !(Efp_a(1) ^ Efp_b(1) ^ Efp_a(0))
  val E_greater = Mux(Efp_b_is_greater,Efp_b,Efp_a)
  val absEaSubEb = Efp_a(0) ^ Efp_b(0)
  val exp_is_equal = !absEaSubEb | (!Efp_a_is_not_zero ^ !Efp_b_is_not_zero)
  val significand_fp_a = Cat(Efp_a_is_not_zero, fp_a_mantissa)
  val significand_fp_b = Cat(Efp_b_is_not_zero, fp_b_mantissa)
  val EA = Mux(Efp_b_is_greater, Efp_b, Efp_a)
  val EA_reg = RegEnable(EA, fire)

  val B_guard = Mux(Efp_b_is_greater,
    Mux(Efp_a_is_not_zero, Mux(res_is_f32,fp_a_mantissa(0),fp_a_mantissa(0+13)), false.B),
    Mux(Efp_b_is_not_zero, Mux(res_is_f32,fp_b_mantissa(0),fp_b_mantissa(0+13)), false.B)
  )
  val B_round = false.B
  val B_sticky = false.B

  val mask_Efp_a_onehot = Cat(
    Efp_a === 0.U | Efp_a === 1.U,
    (for (i <- 2 until significandWidth + 1) yield
      (Efp_a === i.U).asUInt
      ).reduce(Cat(_, _))
  )
  val mask_Efp_b_onehot = Cat(
    Efp_b === 0.U | Efp_b === 1.U,
    (for (i <- 2 until significandWidth + 1) yield
      (Efp_b === i.U).asUInt
      ).reduce(Cat(_, _))
  )

  val U_CS0 = Module(new ClosePathF32WidenF16MixedPipelineAdder(adderWidth = significandWidth, adderType = "CS0"))
  U_CS0.io.adder_op0 := significand_fp_a
  U_CS0.io.adder_op1 := significand_fp_b
  val CS0 = U_CS0.io.result(significandWidth-1,0)
  val priority_lshift_0 = CS0 | mask_Efp_a_onehot

  val U_CS1 = Module(new ClosePathF32WidenF16MixedPipelineAdder(adderWidth = significandWidth, adderType = "CS1"))
  U_CS1.io.adder_op0 := significand_fp_b
  U_CS1.io.adder_op1 := significand_fp_a
  val CS1 = U_CS1.io.result(significandWidth-1,0)
  val priority_lshift_1 = CS1 | mask_Efp_b_onehot

  val U_CS2 = Module(new ClosePathF32WidenF16MixedPipelineAdder(adderWidth = significandWidth, adderType = "CS2"))
  U_CS2.io.adder_op0 := Cat(1.U,fp_a_mantissa)
  U_CS2.io.adder_op1 := Cat(1.U,fp_b_mantissa)
  val CS2 = U_CS2.io.result(significandWidth,0)
  val priority_lshift_2 = CS2 | Cat(mask_Efp_a_onehot,Efp_a===(significandWidth+1).U)

  val U_CS3 = Module(new ClosePathF32WidenF16MixedPipelineAdder(adderWidth = significandWidth, adderType = "CS3"))
  U_CS3.io.adder_op0 := Cat(1.U,fp_b_mantissa)
  U_CS3.io.adder_op1 := Cat(1.U,fp_a_mantissa)
  val CS3 = U_CS3.io.result(significandWidth,0)
  val priority_lshift_3 = CS3 | Cat(mask_Efp_b_onehot,Efp_b===(significandWidth+1).U)

  val U_CS4 = Module(new ClosePathF32WidenF16MixedPipelineAdder(adderWidth = significandWidth, adderType = "CS0"))
  U_CS4.io.adder_op0 := RegEnable(Mux(Efp_b_is_greater,significand_fp_b,significand_fp_a), fire)
  U_CS4.io.adder_op1 := RegEnable(Mux(
    Efp_b_is_greater,
    Cat(0.U,Mux(res_is_f32,significand_fp_a(significandWidth-1,1),Cat(significand_fp_a(significandWidth-1,1+13),0.U(13.W)))),
    Cat(0.U,Mux(res_is_f32,significand_fp_b(significandWidth-1,1),Cat(significand_fp_b(significandWidth-1,1+13),0.U(13.W))))
  ), fire)
  val CS4_reg = U_CS4.io.result

  val close_sign_result_reg = Wire(Bool())
  val close_exponent_result_reg = Wire(UInt(exponentWidth.W))
  val close_fraction_result_reg = Wire(UInt((significandWidth - 1).W))
  val CS2_round_up = Mux(res_is_f32,significand_fp_b(0),significand_fp_b(0+13)) & (
    (RUP & !fp_a_sign) | (RDN & fp_a_sign) | (RNE & Mux(res_is_f32,CS2(1),CS2(1+13)) & Mux(res_is_f32,CS2(0),CS2(0+13))) | RMM
    )
  val CS3_round_up = Mux(res_is_f32,significand_fp_a(0),significand_fp_a(0+13)) & (
    (RUP & fp_a_sign) | (RDN & !fp_a_sign) | (RNE & Mux(res_is_f32,CS3(1),CS3(1+13)) & Mux(res_is_f32,CS3(0),CS3(0+13))) | RMM
    )
  val sel_CS0 = exp_is_equal & !U_CS0.io.result.head(1).asBool
  val sel_CS1 = exp_is_equal & U_CS0.io.result.head(1).asBool
  val sel_CS2 = !exp_is_equal & !Efp_b_is_greater & ((!U_CS2.io.result.head(1).asBool | Mux(res_is_f32,!U_CS2.io.result(0),!U_CS2.io.result(0+13)).asBool) | !CS2_round_up)
  val sel_CS3 = !exp_is_equal &  Efp_b_is_greater & ((!U_CS3.io.result.head(1).asBool | Mux(res_is_f32,!U_CS3.io.result(0),!U_CS3.io.result(0+13)).asBool) | !CS3_round_up)
  val sel_CS4 = !exp_is_equal & (
    (!Efp_b_is_greater & U_CS2.io.result.head(1).asBool & Mux(res_is_f32,U_CS2.io.result(0),U_CS2.io.result(0+13)).asBool & CS2_round_up) |
      (Efp_b_is_greater & U_CS3.io.result.head(1).asBool & Mux(res_is_f32,U_CS3.io.result(0),U_CS3.io.result(0+13)).asBool & CS3_round_up)
    )
  val CS_0123_result = Mux1H(
    Seq(
      sel_CS0,
      sel_CS1,
      sel_CS2,
      sel_CS3
    ),
    Seq(
      Cat(CS0,0.U),
      Cat(CS1,0.U),
      CS2,
      CS3
    )
  )
  val mask_onehot = Mux1H(
    Seq(
      sel_CS0,
      sel_CS1,
      sel_CS2,
      sel_CS3
    ),
    Seq(
      Cat(mask_Efp_a_onehot,1.U),
      Cat(mask_Efp_b_onehot,1.U),
      Cat(mask_Efp_a_onehot,Efp_a===(significandWidth+1).U),
      Cat(mask_Efp_b_onehot,Efp_b===(significandWidth+1).U)
    )
  )
  val priority_mask = CS_0123_result | mask_onehot
  val lzd_0123 = LZD(priority_mask)
  val lzd_0123_reg = RegEnable(lzd_0123, fire)
  val U_Lshift = Module(new CloseShiftLeftWithMux(CS_0123_result.getWidth,priority_mask.getWidth.U.getWidth))
  U_Lshift.io.src := RegEnable(CS_0123_result, fire)
  U_Lshift.io.shiftValue := lzd_0123_reg
  val CS_0123_lshift_result_reg = U_Lshift.io.result(significandWidth,1)
  NX_reg := RegEnable(Mux(
    (sel_CS2 & (U_CS2.io.result.head(1).asBool & Mux(res_is_f32,U_CS2.io.result(0),U_CS2.io.result(0+13)).asBool & !CS2_round_up)) |
      (sel_CS3 & (U_CS3.io.result.head(1).asBool & Mux(res_is_f32,U_CS3.io.result(0),U_CS3.io.result(0+13)).asBool & !CS3_round_up)) | sel_CS4,
    B_guard,
    0.U
  ), fire)
  close_fraction_result_reg := Mux(RegEnable(sel_CS4, fire),CS4_reg.tail(1),CS_0123_lshift_result_reg.tail(1))
  val lshift_result_head_is_one_reg = ((EA_reg-1.U > lzd_0123_reg) & RegEnable(CS_0123_result.orR, fire)) | RegEnable((mask_onehot & CS_0123_result).orR, fire)
  val EA_sub_value_reg = Mux(
    RegEnable(sel_CS4, fire),
    0.U(exponentWidth.W),
    Mux(
      lshift_result_head_is_one_reg,
      RegEnable(lzd_0123.asTypeOf(EA), fire),
      EA_reg
    )
  )
  close_exponent_result_reg := EA_reg - EA_sub_value_reg
  close_sign_result_reg := RegEnable(Mux1H(
    Seq(
      sel_CS0 & (exp_is_equal & (U_CS0.io.result.head(1).asBool | U_CS1.io.result.head(1).asBool)),
      sel_CS0 & exp_is_equal & (!U_CS0.io.result.head(1).asBool & !U_CS1.io.result.head(1).asBool),
      sel_CS1,
      sel_CS2,
      sel_CS3,
      sel_CS4
    ),
    Seq(
      fp_a_sign,
      RDN,
      !fp_a_sign,
      fp_a_sign,
      !fp_a_sign,
      Mux(Efp_b_is_greater,!fp_a_sign,fp_a_sign)
    )
  ), fire)
  io.fp_c := Cat(close_sign_result_reg,close_exponent_result_reg,close_fraction_result_reg)
  if (hasMinMaxCompare) io.CS1 := U_CS1.io.result
  else io.CS1 := 0.U
}


class ClosePathF16Pipeline(
                                          exponentWidth : Int = 11,
                                          var significandWidth : Int = 53,
                                          val is_print:Boolean = false,
                                          val hasMinMaxCompare:Boolean = false) extends Module {
  val floatWidth = exponentWidth + significandWidth
  val io = IO(new Bundle() {
    val fire        = Input (Bool())
    val fp_a, fp_b  = Input (UInt(floatWidth.W))
    val fp_c        = Output(UInt(floatWidth.W))
    val round_mode  = Input (UInt(3.W))
    val fflags      = Output(UInt(5.W))
    val CS1         = if (hasMinMaxCompare) Output(UInt((significandWidth+1).W)) else Output(UInt(0.W))
  })
  val fire = io.fire
  val fp_a_sign = io.fp_a.head(1).asBool
  val fp_b_sign = io.fp_b.head(1).asBool
  val RNE = io.round_mode === "b000".U
  val RTZ = io.round_mode === "b001".U
  val RDN = io.round_mode === "b010".U
  val RUP = io.round_mode === "b011".U
  val RMM = io.round_mode === "b100".U
  val NV = WireInit(false.B)
  val DZ = WireInit(false.B)
  val OF = WireInit(false.B)
  val UF = WireInit(false.B)
  val NX = WireInit(false.B)
  io.fflags := Cat(NV,DZ,OF,UF,NX)
  val fp_a_mantissa = io.fp_a.tail(1 + exponentWidth)
  val fp_b_mantissa = io.fp_b.tail(1 + exponentWidth)
  val Efp_a = io.fp_a(floatWidth - 2, floatWidth - 1 - exponentWidth)
  val Efp_b = io.fp_b(floatWidth - 2, floatWidth - 1 - exponentWidth)
  val Efp_a_is_not_zero = Efp_a.orR
  val Efp_b_is_not_zero = Efp_b.orR
  val Efp_b_is_greater = (Efp_b(0) ^ Efp_a(0)) & !(Efp_a(1) ^ Efp_b(1) ^ Efp_a(0))
  val absEaSubEb = Efp_a(0) ^ Efp_b(0)
  val exp_is_equal = !absEaSubEb | (!Efp_a_is_not_zero ^ !Efp_b_is_not_zero)
  val significand_fp_a = Cat(Efp_a_is_not_zero, fp_a_mantissa)
  val significand_fp_b = Cat(Efp_b_is_not_zero, fp_b_mantissa)
  val EA = Mux(Efp_b_is_greater, Efp_b, Efp_a)

  val B_guard = Mux(Efp_b_is_greater, Mux(Efp_a_is_not_zero, fp_a_mantissa(0), false.B),
    Mux(Efp_b_is_not_zero, fp_b_mantissa(0), false.B))
  val B_round = false.B
  val B_sticky = false.B

  val mask_Efp_a_onehot = Cat(
    Efp_a === 0.U | Efp_a === 1.U,
    (for (i <- 2 until significandWidth + 1) yield
      (Efp_a === i.U).asUInt
      ).reduce(Cat(_, _))
  )
  val mask_Efp_b_onehot = Cat(
    Efp_b === 0.U | Efp_b === 1.U,
    (for (i <- 2 until significandWidth + 1) yield
      (Efp_b === i.U).asUInt
      ).reduce(Cat(_, _))
  )
  val U_CS0 = Module(new ClosePathAdderF16Pipeline(adderWidth = significandWidth, adderType = "CS0"))
  U_CS0.io.adder_op0 := significand_fp_a
  U_CS0.io.adder_op1 := significand_fp_b
  val CS0 = U_CS0.io.result(significandWidth-1,0)
  val priority_lshift_0 = CS0 | mask_Efp_a_onehot

  val U_CS1 = Module(new ClosePathAdderF16Pipeline(adderWidth = significandWidth, adderType = "CS1"))
  U_CS1.io.adder_op0 := significand_fp_b
  U_CS1.io.adder_op1 := significand_fp_a
  val CS1 = U_CS1.io.result(significandWidth-1,0)
  val priority_lshift_1 = CS1 | mask_Efp_b_onehot

  val U_CS2 = Module(new ClosePathAdderF16Pipeline(adderWidth = significandWidth, adderType = "CS2"))
  U_CS2.io.adder_op0 := Cat(1.U,fp_a_mantissa)
  U_CS2.io.adder_op1 := Cat(1.U,fp_b_mantissa)
  val CS2 = U_CS2.io.result(significandWidth,0)
  val priority_lshift_2 = CS2 | Cat(mask_Efp_a_onehot,Efp_a===(significandWidth+1).U)

  val U_CS3 = Module(new ClosePathAdderF16Pipeline(adderWidth = significandWidth, adderType = "CS3"))
  U_CS3.io.adder_op0 := Cat(1.U,fp_b_mantissa)
  U_CS3.io.adder_op1 := Cat(1.U,fp_a_mantissa)
  val CS3 = U_CS3.io.result(significandWidth,0)
  val priority_lshift_3 = CS3 | Cat(mask_Efp_b_onehot,Efp_b===(significandWidth+1).U)

  val U_CS4 = Module(new ClosePathAdderF16Pipeline(adderWidth = significandWidth, adderType = "CS0"))
  U_CS4.io.adder_op0 := RegEnable(Mux(Efp_b_is_greater,significand_fp_b,significand_fp_a), fire)
  U_CS4.io.adder_op1 := RegEnable(Mux(Efp_b_is_greater,Cat(0.U,significand_fp_a(significandWidth-1,1)),Cat(0.U,significand_fp_b(significandWidth-1,1))), fire)
  val CS4_reg = U_CS4.io.result

  val close_sign_result = Wire(Bool())
  val close_exponent_result = Wire(UInt(exponentWidth.W))
  val close_fraction_result = Wire(UInt((significandWidth - 1).W))
  val CS2_round_up = significand_fp_b(0) & (
    (RUP & !fp_a_sign) | (RDN & fp_a_sign) | (RNE & CS2(1) & CS2(0)) | RMM
    )
  val CS3_round_up = significand_fp_a(0) & (
    (RUP & fp_a_sign) | (RDN & !fp_a_sign) | (RNE & CS3(1) & CS3(0)) | RMM
    )
  val sel_CS0 = exp_is_equal & !U_CS0.io.result.head(1).asBool
  val sel_CS1 = exp_is_equal & U_CS0.io.result.head(1).asBool
  val sel_CS2 = !exp_is_equal & !Efp_b_is_greater & ((!U_CS2.io.result.head(1).asBool | !U_CS2.io.result(0).asBool) | !CS2_round_up)
  val sel_CS3 = !exp_is_equal &  Efp_b_is_greater & ((!U_CS3.io.result.head(1).asBool | !U_CS3.io.result(0).asBool) | !CS3_round_up)
  val sel_CS4 = !exp_is_equal & (
    (!Efp_b_is_greater & U_CS2.io.result.head(1).asBool & U_CS2.io.result(0).asBool & CS2_round_up) |
      (Efp_b_is_greater & U_CS3.io.result.head(1).asBool & U_CS3.io.result(0).asBool & CS3_round_up)
    )
  val CS_0123_result = Mux1H(
    Seq(
      sel_CS0,
      sel_CS1,
      sel_CS2,
      sel_CS3
    ),
    Seq(
      Cat(CS0,0.U),
      Cat(CS1,0.U),
      CS2,
      CS3
    )
  )
  val mask_onehot = Mux1H(
    Seq(
      sel_CS0,
      sel_CS1,
      sel_CS2,
      sel_CS3
    ),
    Seq(
      Cat(mask_Efp_a_onehot,1.U),
      Cat(mask_Efp_b_onehot,1.U),
      Cat(mask_Efp_a_onehot,Efp_a===(significandWidth+1).U),
      Cat(mask_Efp_b_onehot,Efp_b===(significandWidth+1).U)
    )
  )
  val priority_mask = CS_0123_result | mask_onehot





  val lzd_0123 = LZD(priority_mask)
  val lzd_0123_reg = RegEnable(lzd_0123, fire)
  val U_Lshift = Module(new CloseShiftLeftWithMux(CS_0123_result.getWidth,priority_mask.getWidth.U.getWidth))
  U_Lshift.io.src := RegEnable(CS_0123_result, fire)
  U_Lshift.io.shiftValue := lzd_0123_reg
  val CS_0123_lshift_result_reg = U_Lshift.io.result(significandWidth,1)
  NX := RegEnable(Mux(
    (sel_CS2 & (U_CS2.io.result.head(1).asBool & U_CS2.io.result(0).asBool & !CS2_round_up)) |
      (sel_CS3 & (U_CS3.io.result.head(1).asBool & U_CS3.io.result(0).asBool & !CS3_round_up)) | sel_CS4,
    B_guard,
    0.U
  ), fire)
  close_fraction_result := Mux(RegEnable(sel_CS4, fire),CS4_reg.tail(1),CS_0123_lshift_result_reg.tail(1))

  val lshift_result_head_is_one = ((RegEnable(EA-1.U, fire) > lzd_0123_reg) & RegEnable(CS_0123_result, fire).orR) | RegEnable(mask_onehot & CS_0123_result, fire).orR
  val EA_sub_value = Mux(
    RegEnable(sel_CS4, fire),
    0.U(exponentWidth.W),
    Mux(
      lshift_result_head_is_one,
      lzd_0123_reg.asTypeOf(EA),
      RegEnable(EA, fire)
    )
  )
  close_exponent_result := RegEnable(EA, fire) - EA_sub_value
  close_sign_result := RegEnable(Mux1H(
    Seq(
      sel_CS0 & (exp_is_equal & (U_CS0.io.result.head(1).asBool | U_CS1.io.result.head(1).asBool)),
      sel_CS0 & exp_is_equal & (!U_CS0.io.result.head(1).asBool & !U_CS1.io.result.head(1).asBool),
      sel_CS1,
      sel_CS2,
      sel_CS3,
      sel_CS4
    ),
    Seq(
      fp_a_sign,
      RDN,
      !fp_a_sign,
      fp_a_sign,
      !fp_a_sign,
      Mux(Efp_b_is_greater,!fp_a_sign,fp_a_sign)
    )
  ), fire)
  io.fp_c := Cat(close_sign_result,close_exponent_result,close_fraction_result)
  if (hasMinMaxCompare) io.CS1 := U_CS1.io.result
  else io.CS1 := 0.U
  if (is_print){
    printf(p"*****close path*****\n")
    printf(p"CS1 = ${Binary(CS1)}\n")
    printf(p"CS0 = ${Binary(CS0)}\n")
  }

}

class FarPathAdderF16Pipeline(val AW:Int, val AdderType:String, val stage0AdderWidth: Int = 0) extends Module {
  val io = IO(new Bundle() {
    val fire = Input(Bool())
    val A   = Input (UInt(AW.W))
    val B   = Input (UInt(AW.W))
    val result = Output(UInt(AW.W))
  })
  val fire = io.fire
  if (stage0AdderWidth == 0) io.result  := RegEnable(io.A, fire) + RegEnable(io.B, fire)
  else if (stage0AdderWidth > 0) {
    val short_adder_num = AW/stage0AdderWidth + 1
    val seq_short_full_adder = (for (i <- 0 until short_adder_num) yield {
      if (i == (short_adder_num - 1)) io.A(AW-1,stage0AdderWidth*i) + io.B(AW-1,stage0AdderWidth*i)
      else io.A(stage0AdderWidth*(i+1)-1,stage0AdderWidth*i) +& io.B(stage0AdderWidth*(i+1)-1,stage0AdderWidth*i)
    }).reverse.reduce(Cat(_,_))
    val reg_short_adder_sum = (for (i <- 0 until short_adder_num) yield {
      if (i == (short_adder_num - 1)) seq_short_full_adder(seq_short_full_adder.getWidth-1,(stage0AdderWidth+1)*i)
      else seq_short_full_adder((stage0AdderWidth+1)*i+stage0AdderWidth-1,(stage0AdderWidth+1)*i)
    }).reverse.reduce(Cat(_,_))
    val reg_short_adder_car = (for (i <- 0 until short_adder_num) yield {
      if (i == (short_adder_num - 1)) 0.U((AW-stage0AdderWidth*i-1).W)
      else Cat(seq_short_full_adder((stage0AdderWidth+1)*(i+1)-1),0.U((stage0AdderWidth-1).W))
    }).reverse.reduce(Cat(_,_))
    io.result := Cat(
      RegEnable(reg_short_adder_sum(AW-1,stage0AdderWidth), fire) + RegEnable(Cat(reg_short_adder_car,0.U)(AW-1,stage0AdderWidth), fire),
      RegEnable(reg_short_adder_sum(stage0AdderWidth-1,0), fire)
    )
  }
}



class FarPathF16Pipeline(
                                        exponentWidth : Int = 5,
                                        significandWidth : Int = 11,
                                        val is_print:Boolean = false,
                                        val hasMinMaxCompare:Boolean = false) extends Module {
  val floatWidth = exponentWidth + significandWidth
  val io = IO(new Bundle() {
    val fire        = Input (Bool())
    val fp_a, fp_b  = Input (UInt(floatWidth.W))
    val fp_c        = Output(UInt(floatWidth.W))
    val is_sub      = Input (Bool())
    val round_mode  = Input (UInt(3.W))
    val fflags      = Output(UInt(5.W))
    val absEaSubEb  = Output(UInt(exponentWidth.W))
    val isEfp_bGreater = if (hasMinMaxCompare) Output(UInt(1.W)) else Output(UInt(0.W))
  })
  val fire = io.fire
  val fp_a_sign = io.fp_a.head(1).asBool
  val fp_b_sign = io.fp_b.head(1).asBool
  val efficient_fp_b_sign = (fp_b_sign ^ io.is_sub).asBool
  val EOP = ( fp_a_sign ^ efficient_fp_b_sign ).asBool
  val EOP_reg = RegEnable(EOP, fire)
  val RNE_reg = RegEnable(io.round_mode === "b000".U, fire)
  val RTZ_reg = RegEnable(io.round_mode === "b001".U, fire)
  val RDN_reg = RegEnable(io.round_mode === "b010".U, fire)
  val RUP_reg = RegEnable(io.round_mode === "b011".U, fire)
  val RMM_reg = RegEnable(io.round_mode === "b100".U, fire)
  val NV  = WireInit(false.B)
  val DZ  = WireInit(false.B)
  val OF  = WireInit(false.B)
  val UF  = WireInit(false.B)
  val NX  = WireInit(false.B)
  io.fflags := Cat(NV,DZ,OF,UF,NX)
  val fp_a_mantissa            = io.fp_a.tail(1 + exponentWidth)
  val fp_b_mantissa            = io.fp_b.tail(1 + exponentWidth)
  val fp_a_mantissa_isnot_zero = io.fp_a.tail(1 + exponentWidth).orR
  val fp_b_mantissa_isnot_zero = io.fp_b.tail(1 + exponentWidth).orR
  val Efp_a = io.fp_a(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_b = io.fp_b(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_a_is_not_zero  = Efp_a.orR
  val Efp_b_is_not_zero  = Efp_b.orR
  val Efp_a_is_greater_than_1= Efp_a.head(exponentWidth-1).orR
  val Efp_b_is_greater_than_1= Efp_b.head(exponentWidth-1).orR
  val Efp_a_is_all_one   = Efp_a.andR
  val Efp_b_is_all_one   = Efp_b.andR
  val U_Efp_aSubEfp_b = Module(new Adder(Efp_a.getWidth,Efp_a.getWidth,Efp_a.getWidth,is_sub = true))
  U_Efp_aSubEfp_b.io.a := Efp_a | !Efp_a_is_not_zero
  U_Efp_aSubEfp_b.io.b := Efp_b | !Efp_b_is_not_zero
  val Efp_aSubEfp_b     = U_Efp_aSubEfp_b.io.c
  val U_Efp_bSubEfp_a = Module(new Adder(Efp_a.getWidth,Efp_a.getWidth,Efp_a.getWidth,is_sub = true))
  U_Efp_bSubEfp_a.io.a := Efp_b | !Efp_b_is_not_zero
  U_Efp_bSubEfp_a.io.b := Efp_a | !Efp_a_is_not_zero
  val Efp_bSubEfp_a     = U_Efp_bSubEfp_a.io.c
  val isEfp_bGreater    = Efp_b > Efp_a
  val Efp_a_sub_1       = Mux(Efp_a_is_not_zero,Efp_a - 1.U,0.U)
  val Efp_b_sub_1       = Mux(Efp_b_is_not_zero,Efp_b - 1.U,0.U)
  val absEaSubEb        = Wire(UInt(Efp_a.getWidth.W))
  io.absEaSubEb := absEaSubEb
  absEaSubEb := Mux(isEfp_bGreater, Efp_bSubEfp_a, Efp_aSubEfp_b)
  val significand_fp_a   = Cat(Efp_a_is_not_zero,fp_a_mantissa)
  val significand_fp_b   = Cat(Efp_b_is_not_zero,fp_b_mantissa)
  val E_greater          = Mux(isEfp_bGreater, Efp_b, Efp_a)
  val EA                 = Mux(EOP, E_greater-1.U, E_greater)
  val EA_add1            = EA + 1.U
  val greaterSignificand = Mux(isEfp_bGreater, significand_fp_b, significand_fp_a)
  val smallerSignificand = Mux(isEfp_bGreater, significand_fp_a, significand_fp_b)
  val farmaxShiftValue   = (significandWidth+2).U
  val A_wire              = Mux(EOP,Cat(greaterSignificand,0.U),Cat(0.U,greaterSignificand))
  val A_wire_FS1 = Mux(EOP,Cat(greaterSignificand,0.U),Cat(0.U,greaterSignificand)) + 2.U
  val widenWidth = significandWidth + 3
  val fp_b_mantissa_widen = Mux(EOP,Cat(~significand_fp_b,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_b,0.U(widenWidth.W)))
  val U_far_rshift_1 = Module(new FarShiftRightWithMuxInvFirst(fp_b_mantissa_widen.getWidth,farmaxShiftValue.getWidth))
  U_far_rshift_1.io.src := fp_b_mantissa_widen
  U_far_rshift_1.io.shiftValue := Efp_aSubEfp_b.asTypeOf(farmaxShiftValue)
  U_far_rshift_1.io.EOP := EOP
  val U_far_rshift_fp_b_result = U_far_rshift_1.io.result
  val fp_a_mantissa_widen = Mux(EOP,Cat(~significand_fp_a,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_a,0.U(widenWidth.W)))
  val U_far_rshift_2 = Module(new FarShiftRightWithMuxInvFirst(fp_a_mantissa_widen.getWidth,farmaxShiftValue.getWidth))
  U_far_rshift_2.io.src := fp_a_mantissa_widen
  U_far_rshift_2.io.shiftValue := Efp_bSubEfp_a.asTypeOf(farmaxShiftValue)
  U_far_rshift_2.io.EOP := EOP
  val U_far_rshift_fp_a_result = U_far_rshift_2.io.result

  val far_rshift_widen_result = Mux(isEfp_bGreater,U_far_rshift_fp_a_result,U_far_rshift_fp_b_result)
  val absEaSubEb_is_greater = absEaSubEb > (significandWidth + 3).U
  val B_wire = Mux(absEaSubEb_is_greater,Fill(significandWidth+1,EOP),far_rshift_widen_result.head(significandWidth+1))
  val B_guard_normal_reg     = RegEnable(Mux(
    absEaSubEb_is_greater,
    false.B,
    Mux(EOP,!far_rshift_widen_result.head(significandWidth+2)(0).asBool,far_rshift_widen_result.head(significandWidth+2)(0).asBool)
  ), fire)
  val B_round_normal_reg     = RegEnable(Mux(
    absEaSubEb_is_greater,
    false.B,
    Mux(EOP,!far_rshift_widen_result.head(significandWidth+3)(0).asBool,far_rshift_widen_result.head(significandWidth+3)(0).asBool)
  ), fire)
  val B_sticky_normal_reg    = Mux(
    RegEnable(absEaSubEb_is_greater, fire),
    RegEnable(smallerSignificand.orR, fire),
    Mux(RegEnable(EOP, fire),RegEnable(~far_rshift_widen_result.tail(significandWidth+3), fire).asUInt.orR,RegEnable(far_rshift_widen_result.tail(significandWidth+3), fire).orR)
  )
  val B_rsticky_normal_reg   = B_round_normal_reg | B_sticky_normal_reg
  val B_guard_overflow_reg   = RegEnable(Mux(
    absEaSubEb_is_greater,
    false.B,
    Mux(EOP,!far_rshift_widen_result.head(significandWidth+1)(0).asBool,far_rshift_widen_result.head(significandWidth+1)(0).asBool)
  ), fire)
  val B_round_overflow_reg   = B_guard_normal_reg
  val B_sticky_overflow_reg  = B_round_normal_reg | B_sticky_normal_reg
  val B_rsticky_overflow_reg = B_round_overflow_reg | B_sticky_overflow_reg
  val U_FS0 = Module(new FarPathAdderF16Pipeline(AW = significandWidth+1, AdderType = "FS0", stage0AdderWidth = 0))
  U_FS0.io.fire := fire
  U_FS0.io.A := A_wire
  U_FS0.io.B := B_wire
  val FS0 = U_FS0.io.result
  val U_FS1 = Module(new FarPathAdderF16Pipeline(AW = significandWidth+1, AdderType = "FS1", stage0AdderWidth = 0))
  U_FS1.io.fire := fire
  U_FS1.io.A := A_wire_FS1
  U_FS1.io.B := B_wire
  val FS1 = U_FS1.io.result
  val far_case_normal   = !FS0.head(1).asBool
  val far_case_overflow = FS0.head(1).asBool
  val lgs_normal_reg = Cat(FS0(0),Mux(EOP_reg,(~Cat(B_guard_normal_reg,B_rsticky_normal_reg)).asUInt+1.U,Cat(B_guard_normal_reg,B_rsticky_normal_reg)))

  val far_sign_result_reg = RegEnable(Mux(isEfp_bGreater, efficient_fp_b_sign, fp_a_sign), fire)
  val far_case_normal_round_up = (EOP_reg & !lgs_normal_reg(1) & !lgs_normal_reg(0)) |
    (RNE_reg & lgs_normal_reg(1) & (lgs_normal_reg(2) | lgs_normal_reg(0))) |
    (RDN_reg & far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
    (RUP_reg & !far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
    (RMM_reg & lgs_normal_reg(1))
  val normal_fsel0 = (!FS0(0) & far_case_normal_round_up) | !far_case_normal_round_up
  val normal_fsel1 = FS0(0) & far_case_normal_round_up
  val grs_overflow = Mux(EOP_reg,Cat(RegEnable(A_wire(0), fire),0.U,0.U) - Cat(RegEnable(~B_wire(0), fire),B_guard_normal_reg,B_rsticky_normal_reg),Cat(FS0(0),B_guard_normal_reg,B_rsticky_normal_reg))
  val lgs_overflow = Cat(FS0(1),grs_overflow(2),grs_overflow(1) | grs_overflow(0))
  val far_case_overflow_round_up = (EOP_reg & !lgs_overflow(1) & !lgs_overflow(0)) |
    (RNE_reg & lgs_overflow(1) & (lgs_overflow(2) | lgs_overflow(0))) |
    (RDN_reg & far_sign_result_reg & (lgs_overflow(1) | lgs_overflow(0))) |
    (RUP_reg & !far_sign_result_reg & (lgs_overflow(1) | lgs_overflow(0))) |
    (RMM_reg & lgs_overflow(1))
  val overflow_fsel0 = (!FS0(1) & far_case_overflow_round_up) | !far_case_overflow_round_up
  val overflow_fsel1 =  FS0(1) & far_case_overflow_round_up
  val far_exponent_result = Wire(UInt(exponentWidth.W))
  val far_fraction_result = Wire(UInt((significandWidth-1).W))
  far_exponent_result := Mux(
    far_case_overflow | (FS1.head(1).asBool & FS0(0) & far_case_normal_round_up) | (RegEnable(!EA.orR, fire) & FS0.tail(1).head(1).asBool),
    RegEnable(EA_add1, fire),
    RegEnable(EA, fire)
  )
  OF := RegEnable(EA_add1.andR, fire) & (far_case_overflow | (FS1.head(1).asBool & FS0(0) & far_case_normal_round_up))
  NX := Mux(far_case_normal,lgs_normal_reg(1,0).orR,lgs_overflow(1,0).orR) | OF
  far_fraction_result := Mux1H(
    Seq(
      far_case_normal & normal_fsel0,
      far_case_normal & normal_fsel1,
      far_case_overflow & overflow_fsel0,
      far_case_overflow & overflow_fsel1
    ),
    Seq(
      Cat(FS0(significandWidth-2,1),FS0(0) ^ far_case_normal_round_up),
      Cat(FS1(significandWidth-2,1),0.U),
      Cat(FS0(significandWidth-1,2),FS0(1) ^ far_case_overflow_round_up),
      FS1(significandWidth-1,1)
    )
  )
  val result_overflow = Mux(
    RTZ_reg | (RDN_reg & !far_sign_result_reg) | (RUP_reg & far_sign_result_reg),
    Cat(far_sign_result_reg,Fill(exponentWidth-1,1.U),0.U,Fill(significandWidth-1,1.U)),
    Cat(far_sign_result_reg,Fill(exponentWidth,1.U),Fill(significandWidth-1,0.U))
  )
  io.fp_c := Mux(OF,
    result_overflow,
    Cat(far_sign_result_reg,far_exponent_result,far_fraction_result)
  )
  if (hasMinMaxCompare) io.isEfp_bGreater := isEfp_bGreater
  else io.isEfp_bGreater := 0.U
}

class ClosePathAdderF16Pipeline(val adderWidth:Int, val adderType:String) extends Module {
  val io = IO(new Bundle() {
    val adder_op0    = Input(UInt(adderWidth.W))
    val adder_op1    = Input(UInt(adderWidth.W))
    val result       = Output(UInt((adderWidth+1).W))
  })
  if (adderType == "CS0" | adderType == "CS1") {
    io.result  :=  Cat(0.U,io.adder_op0) - Cat(0.U,io.adder_op1)
  }
  if (adderType == "CS2" | adderType == "CS3") {
    io.result  :=  Cat(io.adder_op0,0.U) - Cat(0.U,io.adder_op1)
  }
}

class FloatAdderF32WidenF16MixedPipeline(val is_print:Boolean = false,val hasMinMaxCompare:Boolean = false) extends Module {
  val exponentWidth = 8
  val significandWidth = 24
  val floatWidth = exponentWidth + significandWidth
  val io = IO(new Bundle {
    val fire        = Input(Bool())
    val fp_a        = Input(UInt(floatWidth.W))
    val fp_b        = Input(UInt(floatWidth.W))
    val is_sub      = Input(Bool())
    val op_code     = Input(UInt(3.W))
    val fp_format   = Input(UInt(2.W))
    val round_mode  = Input(UInt(3.W))
    val fp_aIsFpCanonicalNAN = Input (Bool())
    val fp_bIsFpCanonicalNAN = Input (Bool())

    val fp_c        = Output(UInt(floatWidth.W))
    val fflags      = Output(UInt(5.W))
  }) 

  val fire = io.fire  

  val res_is_f32 = io.fp_format(0).asBool
  val fp_a_16as32 = Cat(io.fp_a(15), Cat(0.U(3.W),io.fp_a(14,10)), Cat(io.fp_a(9,0),0.U(13.W)))
  val fp_b_16as32 = Cat(io.fp_b(15), Cat(0.U(3.W),io.fp_b(14,10)), Cat(io.fp_b(9,0),0.U(13.W)))

  val fp_a_to32 = Mux(res_is_f32, io.fp_a, fp_a_16as32)
  val fp_b_to32 = Mux(res_is_f32, io.fp_b, fp_b_16as32)

  val EOP = (fp_a_to32.head(1) ^ fp_b_to32.head(1)).asBool
  val U_far_path = Module(new FarPathF32WidenF16MixedPipeline(is_print=is_print, hasMinMaxCompare=hasMinMaxCompare))
  U_far_path.io.fire := fire
  U_far_path.io.fp_a := fp_a_to32
  U_far_path.io.fp_b := fp_b_to32
  U_far_path.io.is_sub := io.is_sub
  U_far_path.io.round_mode := io.round_mode
  U_far_path.io.res_is_f32 := res_is_f32

  val U_close_path = Module(new ClosePathF32WidenF16MixedPipeline(is_print=is_print, hasMinMaxCompare=hasMinMaxCompare))
  U_close_path.io.fire := fire
  U_close_path.io.fp_a := fp_a_to32
  U_close_path.io.fp_b := fp_b_to32
  U_close_path.io.round_mode := io.round_mode
  U_close_path.res_is_f32 := res_is_f32

  val absEaSubEb = U_far_path.io.absEaSubEb
  val is_close_path   =  EOP & (!absEaSubEb(absEaSubEb.getWidth - 1, 1).orR)
  val fp_a_mantissa            = fp_a_to32.tail(1 + exponentWidth)
  val fp_b_mantissa            = fp_b_to32.tail(1 + exponentWidth)
  val fp_a_mantissa_isnot_zero = fp_a_to32.tail(1 + exponentWidth).orR
  val fp_b_mantissa_isnot_zero = fp_b_to32.tail(1 + exponentWidth).orR
  val fp_a_is_f16 = !res_is_f32
  val fp_b_is_f16 = !res_is_f32
  val Efp_a = fp_a_to32(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_b = fp_b_to32(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_a_is_zero  = !Efp_a.orR | (fp_a_is_f16 & Efp_a==="b01100110".U)
  val Efp_b_is_zero  = !Efp_b.orR | (fp_b_is_f16 & Efp_b==="b01100110".U)

  val Efp_a_is_all_one   = Mux(
    fp_a_is_f16,
    io.fp_a(14,10).andR,
    io.fp_a(30,23).andR
  )
  val Efp_b_is_all_one   = Mux(
    fp_b_is_f16,
    io.fp_b(14,10).andR,
    io.fp_b(30,23).andR
  )

  val fp_a_is_NAN        = io.fp_aIsFpCanonicalNAN | Efp_a_is_all_one & fp_a_mantissa_isnot_zero
  val fp_a_is_SNAN       = !io.fp_aIsFpCanonicalNAN & Efp_a_is_all_one & fp_a_mantissa_isnot_zero & !fp_a_to32(significandWidth-2)
  val fp_b_is_NAN        = io.fp_bIsFpCanonicalNAN | Efp_b_is_all_one & fp_b_mantissa_isnot_zero
  val fp_b_is_SNAN       = !io.fp_bIsFpCanonicalNAN & Efp_b_is_all_one & fp_b_mantissa_isnot_zero & !fp_b_to32(significandWidth-2)
  val fp_a_is_infinite   = !io.fp_aIsFpCanonicalNAN & Efp_a_is_all_one & (!fp_a_mantissa_isnot_zero)
  val fp_b_is_infinite   = !io.fp_bIsFpCanonicalNAN & Efp_b_is_all_one & (!fp_b_mantissa_isnot_zero)
  val fp_a_is_zero       = !io.fp_aIsFpCanonicalNAN & Efp_a_is_zero & !fp_a_mantissa_isnot_zero
  val fp_b_is_zero       = !io.fp_bIsFpCanonicalNAN & Efp_b_is_zero & !fp_b_mantissa_isnot_zero


  val is_far_path     = !EOP | (EOP & absEaSubEb(absEaSubEb.getWidth - 1, 1).orR) | (absEaSubEb === 1.U & (Efp_a_is_zero ^ Efp_b_is_zero))
  val is_far_path_reg = RegEnable(is_far_path, fire)
  val float_adder_fflags = Wire(UInt(5.W))
  val float_adder_result = Wire(UInt(floatWidth.W))
  when(RegEnable((fp_a_is_SNAN | fp_b_is_SNAN) | (EOP & fp_a_is_infinite & fp_b_is_infinite), fire)){
    float_adder_fflags := "b10000".U
  }.elsewhen(RegEnable(fp_a_is_NAN | fp_b_is_NAN | fp_a_is_infinite | fp_b_is_infinite, fire)){
    float_adder_fflags := "b00000".U
  }.otherwise{
    float_adder_fflags := Mux(is_far_path_reg,U_far_path.io.fflags,U_close_path.io.fflags)
  }

  val res_is_f32_reg = RegEnable(res_is_f32, fire)
  val out_NAN_reg = Mux(res_is_f32_reg, Cat(0.U,Fill(8,1.U),1.U,0.U(22.W)), Cat(0.U(17.W),Fill(5,1.U),1.U,0.U(9.W)))
  val out_infinite_sign = Mux(fp_a_is_infinite,fp_a_to32.head(1),io.is_sub^fp_b_to32.head(1))
  val out_infinite_sign_reg = RegEnable(out_infinite_sign, fire)
  val out_infinite_reg = Mux(res_is_f32_reg, Cat(out_infinite_sign_reg,Fill(8,1.U),0.U(23.W)), Cat(0.U(16.W),out_infinite_sign_reg,Fill(5,1.U),0.U(10.W)))
  val out_fp32_reg = Mux(is_far_path_reg,U_far_path.io.fp_c,U_close_path.io.fp_c)
  val out_fp32_to_fp16_or_fp32_reg = Mux(res_is_f32_reg, out_fp32_reg, Cat(0.U(16.W),out_fp32_reg(31),out_fp32_reg(27,23),out_fp32_reg(22,13)))

  when(RegEnable(fp_a_is_NAN | fp_b_is_NAN | (EOP & fp_a_is_infinite & fp_b_is_infinite), fire)){
    float_adder_result := out_NAN_reg
  }.elsewhen(RegEnable(fp_a_is_infinite | fp_b_is_infinite, fire)) {
    float_adder_result := out_infinite_reg
  // }.elsewhen(RegEnable(io.res_widening & fp_a_is_zero & fp_b_is_zero, fire)){
  //   float_adder_result := Cat(RegEnable(Mux(io.round_mode==="b010".U & EOP | (fp_a_to32.head(1).asBool & !EOP),1.U,0.U), fire),0.U(31.W))
  // }.elsewhen(RegEnable(io.res_widening & fp_a_is_zero, fire)){
  //   float_adder_result := RegEnable(Cat(io.is_sub ^ fp_b_to32.head(1),fp_b_to32(30,0)), fire)
  // }.elsewhen(RegEnable(io.res_widening & fp_b_is_zero, fire)){
  //   float_adder_result := RegEnable(fp_a_to32, fire)
  }.otherwise{
    float_adder_result := out_fp32_to_fp16_or_fp32_reg
  }

  if (hasMinMaxCompare) {
    //TODO: change to reduction opcode
    val is_add = io.op_code === VfaddOpCode.fadd
    val is_max = io.op_code === VfaddOpCode.fmax
    val is_sub = 0.U.asBool
    val is_min = 0.U.asBool

    val fp_a_sign = fp_a_to32.head(1)
    val fp_b_sign = fp_b_to32.head(1)
    
    val fp_b_sign_is_greater = fp_a_sign & !fp_b_sign
    val fp_b_sign_is_equal   = fp_a_sign === fp_b_sign
    val fp_b_sign_is_smaller = !fp_a_sign & fp_b_sign

    val fp_b_exponent_is_greater = U_far_path.io.isEfp_bGreater
    val fp_b_exponent_is_equal   = Efp_a === Efp_b
    val fp_b_exponent_is_smaller = !fp_b_exponent_is_greater & !fp_b_exponent_is_equal
    val fp_b_significand_is_greater = !U_close_path.io.CS1.head(1) & (fp_a_mantissa =/= fp_b_mantissa)
    val fp_b_significand_is_equal   = fp_a_mantissa === fp_b_mantissa
    val fp_b_significand_is_smaller = !fp_b_significand_is_greater & !fp_b_significand_is_equal
    val fp_b_is_greater = (!fp_b_sign & ((fp_a_sign & !(fp_b_is_zero & fp_a_is_zero)) | fp_b_exponent_is_greater | (fp_b_exponent_is_equal & fp_b_significand_is_greater))) |
                          (fp_b_sign & fp_a_sign & (fp_b_exponent_is_smaller | (fp_b_exponent_is_equal & fp_b_significand_is_smaller)))  
    val fp_b_is_equal = (fp_b_sign_is_equal & fp_b_exponent_is_equal & fp_b_significand_is_equal) | (fp_b_is_zero & fp_a_is_zero)
    val fp_b_is_less = !fp_b_is_greater & !fp_b_is_equal
    
    val result_max = Wire(UInt(floatWidth.W))

    val in_NAN = Mux(res_is_f32, Cat(0.U(1.W),Fill(9, 1.U(1.W)),0.U(22.W)), Cat(0.U(17.W),Fill(6, 1.U(1.W)),0.U(9.W)))
    val fp_aFix = Mux(io.fp_aIsFpCanonicalNAN, in_NAN, io.fp_a)
    val fp_bFix = Mux(io.fp_bIsFpCanonicalNAN, in_NAN, io.fp_b)

    val out_NAN = Mux(res_is_f32, Cat(0.U,Fill(8,1.U),1.U,0.U(22.W)), Cat(0.U(17.W),Fill(5,1.U),1.U,0.U(9.W)))
    val zero_sign = Mux(io.round_mode ==="b010".U, 0.U, 1.U)
    val out_Nzero = Mux(res_is_f32, Cat(zero_sign, 0.U(31.W)), Cat(0.U(16.W), zero_sign, 0.U(15.W)))
    val fp_a_16_or_32 = Mux(res_is_f32, fp_aFix(31, 0), Cat(0.U(16.W), fp_aFix(15, 0)))
    val fp_b_16_or_32 = Mux(res_is_f32, fp_bFix(31, 0), Cat(0.U(16.W), fp_bFix(15, 0)))

    result_max := Mux1H(
      Seq(
        !fp_a_is_NAN & !fp_b_is_NAN,
        !fp_a_is_NAN & fp_b_is_NAN,
        fp_a_is_NAN & !fp_b_is_NAN,
        fp_a_is_NAN & fp_b_is_NAN,
      ),
      Seq(
        Mux(fp_b_is_greater.asBool || (!fp_b_sign.asBool && fp_b_is_zero && fp_a_is_zero), fp_b_16_or_32, fp_a_16_or_32),
        fp_a_16_or_32,
        fp_b_16_or_32,
        out_NAN
      )
    )

    val result_stage0 = Mux1H(
      Seq(
        is_min,
        is_max,
      ),
      Seq(
        0.U,
        result_max,
      )
    )
    val fflags_NV_stage0 = ((is_min | is_max) & (fp_a_is_SNAN | fp_b_is_SNAN))
    val fflags_stage0 = Cat(fflags_NV_stage0,0.U(4.W))
    io.fp_c := Mux(RegEnable(is_add | is_sub, fire),float_adder_result,RegEnable(result_stage0, fire))
    io.fflags := Mux(RegEnable(is_add | is_sub, fire),float_adder_fflags,RegEnable(fflags_stage0, fire))
  }
  else {
    io.fp_c := float_adder_result
    io.fflags := float_adder_fflags
  }
  
}

class FloatAdderF16Pipeline(val is_print:Boolean = false,val hasMinMaxCompare:Boolean = false) extends Module {

  val exponentWidth = 5
  val significandWidth = 11
  val floatWidth = exponentWidth + significandWidth
  val io = IO(new Bundle() {
    val fire        = Input (Bool())
    val fp_a, fp_b  = Input (UInt(floatWidth.W))
    val fp_c        = Output(UInt(floatWidth.W))
    val is_sub      = Input (Bool())
    val mask        = Input (Bool())
    val round_mode  = Input (UInt(3.W))
    val fflags      = Output(UInt(5.W))
    val op_code     = if (hasMinMaxCompare) Input(UInt(5.W)) else Input(UInt(0.W))
    val fp_aIsFpCanonicalNAN = Input(Bool())
    val fp_bIsFpCanonicalNAN = Input(Bool())
  })
  val fire = io.fire
  val EOP = (io.fp_a.head(1) ^ io.is_sub ^ io.fp_b.head(1)).asBool
  val U_far_path = Module(new FarPathF16Pipeline(exponentWidth = exponentWidth,significandWidth = significandWidth, is_print = is_print, hasMinMaxCompare=hasMinMaxCompare))
  U_far_path.io.fire := fire
  U_far_path.io.fp_a := io.fp_a
  U_far_path.io.fp_b := io.fp_b
  U_far_path.io.is_sub := io.is_sub
  U_far_path.io.round_mode := io.round_mode
  val U_close_path = Module(new ClosePathF16Pipeline(exponentWidth = exponentWidth,significandWidth = significandWidth, is_print = is_print, hasMinMaxCompare=hasMinMaxCompare))
  U_close_path.io.fire := fire
  U_close_path.io.fp_a := io.fp_a
  U_close_path.io.fp_b := io.fp_b
  U_close_path.io.round_mode := io.round_mode
  val Efp_a = io.fp_a(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_b = io.fp_b(floatWidth-2, floatWidth-1-exponentWidth)
  val Efp_a_is_not_zero  = Efp_a.orR
  val Efp_b_is_not_zero  = Efp_b.orR
  val Efp_a_is_zero      = !Efp_a_is_not_zero
  val Efp_b_is_zero      = !Efp_b_is_not_zero
  val Efp_a_is_all_one   = Efp_a.andR
  val Efp_b_is_all_one   = Efp_b.andR
  val absEaSubEb = U_far_path.io.absEaSubEb
  val is_far_path     = !EOP | absEaSubEb(absEaSubEb.getWidth - 1, 1).orR | (absEaSubEb === 1.U & (Efp_a_is_not_zero ^ Efp_b_is_not_zero))
  val is_close_path   =  EOP & (!absEaSubEb(absEaSubEb.getWidth - 1, 1).orR)
  val fp_a_mantissa            = io.fp_a.tail(1 + exponentWidth)
  val fp_b_mantissa            = io.fp_b.tail(1 + exponentWidth)
  val fp_a_mantissa_isnot_zero = io.fp_a.tail(1 + exponentWidth).orR
  val fp_b_mantissa_isnot_zero = io.fp_b.tail(1 + exponentWidth).orR
  val fp_a_is_NAN        = io.fp_aIsFpCanonicalNAN | Efp_a_is_all_one & fp_a_mantissa_isnot_zero
  val fp_a_is_SNAN       = !io.fp_aIsFpCanonicalNAN & Efp_a_is_all_one & fp_a_mantissa_isnot_zero & !io.fp_a(significandWidth-2)
  val fp_b_is_NAN        = io.fp_bIsFpCanonicalNAN | Efp_b_is_all_one & fp_b_mantissa_isnot_zero
  val fp_b_is_SNAN       = !io.fp_bIsFpCanonicalNAN & Efp_b_is_all_one & fp_b_mantissa_isnot_zero & !io.fp_b(significandWidth-2)
  val fp_a_is_infinite   = !io.fp_aIsFpCanonicalNAN & Efp_a_is_all_one & (!fp_a_mantissa_isnot_zero)
  val fp_b_is_infinite   = !io.fp_bIsFpCanonicalNAN & Efp_b_is_all_one & (!fp_b_mantissa_isnot_zero)
  val float_adder_fflags = Wire(UInt(5.W))
  val float_adder_result = Wire(UInt(floatWidth.W))
  when(RegEnable((fp_a_is_SNAN | fp_b_is_SNAN) | (EOP & fp_a_is_infinite & fp_b_is_infinite), fire)){
    float_adder_fflags := "b10000".U
  }.elsewhen(RegEnable(fp_a_is_NAN | fp_b_is_NAN | fp_a_is_infinite | fp_b_is_infinite, fire)){
    float_adder_fflags := "b00000".U
  }.otherwise{
    float_adder_fflags := Mux(RegEnable(is_far_path, fire),U_far_path.io.fflags,U_close_path.io.fflags)
  }
  when(RegEnable(fp_a_is_NAN | fp_b_is_NAN | (EOP & fp_a_is_infinite & fp_b_is_infinite), fire)){

    float_adder_result := Cat(0.U,Fill(exponentWidth,1.U),1.U,Fill(significandWidth-2,0.U))
  }.elsewhen(RegEnable(fp_a_is_infinite | fp_b_is_infinite, fire)) {
    float_adder_result := Cat(RegEnable(Mux(fp_a_is_infinite,io.fp_a.head(1),io.is_sub^io.fp_b.head(1)), fire),Fill(exponentWidth,1.U),Fill(significandWidth-1,0.U))
  }.otherwise{
    float_adder_result := Mux(RegEnable(is_far_path, fire),U_far_path.io.fp_c,U_close_path.io.fp_c)
  }
  if (hasMinMaxCompare) {
    val fp_a_is_zero = !io.fp_aIsFpCanonicalNAN & Efp_a_is_zero && !fp_a_mantissa_isnot_zero
    val fp_b_is_zero = !io.fp_bIsFpCanonicalNAN & Efp_b_is_zero && !fp_b_mantissa_isnot_zero

    val is_add = io.op_code === VfaddOpCode.fadd
    val is_sub = io.op_code === VfaddOpCode.fsub
    val is_min = io.op_code === VfaddOpCode.fmin
    val is_max = io.op_code === VfaddOpCode.fmax

    val fp_a_sign = io.fp_a.head(1)
    val fp_b_sign = io.fp_b.head(1)
    val fp_b_sign_is_greater = fp_a_sign & !fp_b_sign
    val fp_b_sign_is_equal   = fp_a_sign === fp_b_sign
    val fp_b_sign_is_smaller = !fp_a_sign & fp_b_sign
    val fp_b_exponent_is_greater = U_far_path.io.isEfp_bGreater
    val fp_b_exponent_is_equal   = Efp_a === Efp_b
    val fp_b_exponent_is_smaller = !fp_b_exponent_is_greater & !fp_b_exponent_is_equal
    val fp_b_significand_is_greater = !U_close_path.io.CS1.head(1) & (fp_a_mantissa =/= fp_b_mantissa)
    val fp_b_significand_is_equal   = fp_a_mantissa === fp_b_mantissa
    val fp_b_significand_is_smaller = !fp_b_significand_is_greater & !fp_b_significand_is_equal
    val fp_b_is_greater = (!fp_b_sign & ((fp_a_sign & !(fp_b_is_zero & fp_a_is_zero)) | fp_b_exponent_is_greater | (fp_b_exponent_is_equal & fp_b_significand_is_greater))) |
                          (fp_b_sign & fp_a_sign & (fp_b_exponent_is_smaller | (fp_b_exponent_is_equal & fp_b_significand_is_smaller)))
    val fp_b_is_equal = (fp_b_sign_is_equal & fp_b_exponent_is_equal & fp_b_significand_is_equal) | (fp_b_is_zero & fp_a_is_zero)
    val fp_b_is_less = !fp_b_is_greater & !fp_b_is_equal
    val result_min = Wire(UInt(floatWidth.W))
    val result_max = Wire(UInt(floatWidth.W))


    val in_NAN = Cat(0.U,Fill(exponentWidth,1.U),1.U,Fill(significandWidth-2,0.U))
    val fp_aFix = Mux(io.fp_aIsFpCanonicalNAN, in_NAN, io.fp_a)
    val fp_bFix = Mux(io.fp_bIsFpCanonicalNAN, in_NAN, io.fp_b)

    val out_NAN = Cat(0.U,Fill(exponentWidth,1.U),1.U,Fill(significandWidth-2,0.U))
    val out_Nzero = Cat(Mux(io.round_mode ==="b010".U, 0.U, 1.U), Fill(floatWidth - 1, 0.U))
    result_min := Mux1H(
      Seq(
        !fp_a_is_NAN & !fp_b_is_NAN,
        !fp_a_is_NAN &  fp_b_is_NAN,
        fp_a_is_NAN & !fp_b_is_NAN,
        fp_a_is_NAN &  fp_b_is_NAN,
      ),
      Seq(
        Mux(fp_b_is_less || (fp_b_sign.asBool && fp_b_is_zero && fp_a_is_zero),io.fp_b,io.fp_a),
        io.fp_a,
        io.fp_b,
        out_NAN
      )
    )
    result_max := Mux1H(
      Seq(
        !fp_a_is_NAN & !fp_b_is_NAN,
        !fp_a_is_NAN &  fp_b_is_NAN,
        fp_a_is_NAN & !fp_b_is_NAN,
        fp_a_is_NAN &  fp_b_is_NAN,
      ),
      Seq(
        Mux(fp_b_is_greater.asBool || (!fp_b_sign.asBool && fp_b_is_zero && fp_a_is_zero),io.fp_b,io.fp_a),
        io.fp_a,
        io.fp_b,
        out_NAN
      )
    )
    
    val result_stage0 = Mux1H(
      Seq(
        is_min,
        is_max,
      ),
      Seq(
        result_min,
        result_max,
      )
    )
    val fflags_NV_stage0 = ((is_min | is_max) & (fp_a_is_SNAN | fp_b_is_SNAN)) 
      
    val fflags_stage0 = Cat(fflags_NV_stage0, 0.U(4.W))
    io.fp_c := Mux(RegEnable(is_add | is_sub, fire), float_adder_result, RegEnable(result_stage0, fire))
    io.fflags := Mux(RegEnable(is_add | is_sub, fire), float_adder_fflags, RegEnable(fflags_stage0, fire))
  }
  else {
    io.fp_c := float_adder_result
    io.fflags := float_adder_fflags
  }
}