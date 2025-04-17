package vector

import chisel3._
import chisel3.util._
import chisel3.stage._
import javax.swing.InputMap
import vector._
import scala.collection.mutable.ListBuffer
import Vreduction._

object FmaOpCode {
  def width = 4

  def fmul    = "b0000".U(width.W)
  def fmacc   = "b0001".U(width.W)
  def fmsac   = "b0011".U(width.W)
  def fnmacc  = "b0010".U(width.W)
  def fnmsac  = "b0100".U(width.W)
}


class GFloatFMA() extends Module with Params {
  val io = IO(new Bundle() {
    val fire                 = Input (Bool())
    val fp_a, fp_b, fp_c     = Input (UInt(floatWidth.W))  // fp_a->VS2,fp_b->VS1,fp_c->VD
    val round_mode           = Input (UInt(3.W))
    val fp_format            = Input (UInt(2.W))
    val op_code              = Input (UInt(4.W))
    val fp_result            = Output(UInt(floatWidth.W))
    val fflags               = Output(UInt(5.W))
    val fp_aIsFpCanonicalNAN = Input(Bool())
    val fp_bIsFpCanonicalNAN = Input(Bool())
    val fp_cIsFpCanonicalNAN = Input(Bool())
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

  def shiftRightWithMuxSticky(srcValue: UInt, shiftValue: UInt): UInt = {
    val vecLength  = shiftValue.getWidth + 1
    val res_vec    = Wire(Vec(vecLength,UInt(srcValue.getWidth.W)))
    val sticky_vec = Wire(Vec(vecLength,UInt(1.W)))
    res_vec(0)    := srcValue
    sticky_vec(0) := 0.U
    for (i <- 0 until shiftValue.getWidth) {
      res_vec(i+1) := Mux(shiftValue(i), res_vec(i) >> (1<<i), res_vec(i))
      sticky_vec(i+1) := Mux(shiftValue(i), sticky_vec(i) | res_vec(i)((1<<i)-1,0).orR, sticky_vec(i))
    }
    Cat(res_vec(vecLength-1),sticky_vec(vecLength-1))
  }

  def calcPipeLevel(n: Int): Int = {
    var n_next = n
    var CSA4to2Num = if(n_next==8 || n_next==4) n_next/4 else 0
    var CSA3to2Num = if(n_next==8 || n_next==4) 0 else n_next/3
    var remainder = n_next - CSA4to2Num*4 - CSA3to2Num*3
    var level = 0
    while (n_next > 2) {
      n_next = (CSA4to2Num+CSA3to2Num)*2 + remainder
      CSA4to2Num = if(n_next==8 || n_next==4) n_next/4 else 0
      CSA3to2Num = if(n_next==8 || n_next==4) 0 else n_next/3
      remainder = n_next - CSA4to2Num*4 - CSA3to2Num*3
      level += 1
    }
    level
  }

  val fire = io.fire
  val fire_reg0 = GatedValidRegNext(fire)
  val fire_reg1 = GatedValidRegNext(fire_reg0)
  val is_fmul   = io.op_code === FmaOpCode.fmul
  val is_fmacc  = io.op_code === FmaOpCode.fmacc
  val is_fnmacc = io.op_code === FmaOpCode.fnmacc
  val is_fmsac  = io.op_code === FmaOpCode.fmsac
  val is_fnmsac = io.op_code === FmaOpCode.fnmsac
  val is_fp                 = true.B
  val is_fp_reg0            = RegEnable(is_fp, fire)
  val is_fp_reg1            = RegEnable(is_fp_reg0, fire_reg0)
  val is_fp_reg2            = RegEnable(is_fp_reg1, fire_reg1)

  val rshiftBasic          = significandWidth + 3
  val rshiftMax            = 3*significandWidth + 4

  // bias
  val bias = (1 << (exponentWidth-1)) - 1

  def sign_inv(src: UInt,sel:Bool): UInt = {
    Cat(Mux(sel,~src.head(1),src.head(1)),src.tail(1))
  }
  val fp_a_is_sign_inv = is_fnmacc || is_fnmsac
  val fp_c_is_sign_inv = is_fnmacc || is_fmsac
  // fp src
  val fp_a              = sign_inv(io.fp_a(floatWidth-1,0),fp_a_is_sign_inv)
  val fp_b              = io.fp_b(floatWidth-1,0)
  val fp_c              = Mux(is_fmul, 0.U(floatWidth.W), sign_inv(io.fp_c(floatWidth-1,0),fp_c_is_sign_inv))

  // is sub
  val sign_a_b          = (fp_a.head(1) ^ fp_b.head(1)).asBool
  val sign_c            = fp_c.head(1).asBool
  val is_sub            = sign_a_b ^ sign_c
  val is_sub_reg0       = RegEnable(is_sub, fire)
  val is_sub_reg1       = RegEnable(is_sub_reg0, fire_reg0)
  val is_sub_reg2       = RegEnable(is_sub_reg1, fire_reg1)

  // exponent
  val Ea                = fp_a.tail(1).head(exponentWidth)
  val Eb                = fp_b.tail(1).head(exponentWidth)
  val Ec                = fp_c.tail(1).head(exponentWidth)

  //exponent not zero
  val Ea_is_not_zero    = Ea.orR
  val Eb_is_not_zero    = Eb.orR
  val Ec_is_not_zero    = Ec.orR

  //siginificand with sign
  val fp_a_significand  = Cat(Ea_is_not_zero,fp_a.tail(exponentWidth+1))
  val fp_b_significand  = Cat(Eb_is_not_zero,fp_b.tail(exponentWidth+1))
  val fp_c_significand  = Cat(Ec_is_not_zero,fp_c.tail(exponentWidth+1))

  //exponent fix
  val Ea_fix            = Cat(Ea.head(exponentWidth-1),!Ea_is_not_zero | Ea(0))
  val Eb_fix            = Cat(Eb.head(exponentWidth-1),!Eb_is_not_zero | Eb(0))
  val Ec_fix            = Cat(Ec.head(exponentWidth-1),!Ec_is_not_zero | Ec(0))

  // exponent after bias and rshift Normalization
  val Eab               = Cat(0.U,Ea_fix +& Eb_fix).asSInt - bias.S + rshiftBasic.S

  // get The difference between the exponent of the product of 𝑎 mul b and the exponent of c  for exponent alignment
  val rshift_value      = Eab - Cat(0.U,Ec_fix).asSInt
  
  // leave space for max rshift
  val rshift_value_cut  = rshift_value(rshiftMax.U.getWidth-1,0)
  val fp_c_significand_cat0  = Cat(fp_c_significand,0.U((rshiftMax-significandWidth).W))

  // rshift_resut
  val rshift_result_with_grs = shiftRightWithMuxSticky(fp_c_significand_cat0,rshift_value_cut)

  // special case
  val Ec_is_too_big          = rshift_value <= 0.S

  val Ec_is_too_small        = rshift_value.asSInt > rshiftMax.S

  val Ec_is_medium           = !Ec_is_too_big & !Ec_is_too_small

  val rshift_guard           = RegEnable(Mux(Ec_is_medium, rshift_result_with_grs(2), 0.U), fire)
  val rshift_round           = RegEnable(Mux(Ec_is_medium, rshift_result_with_grs(1), 0.U), fire)
  val rshift_sticky          = RegEnable(Mux(Ec_is_medium, rshift_result_with_grs(0), Mux(Ec_is_too_big, 0.U, fp_c_significand.orR)), fire)

  val rshift_result_temp     = rshift_result_with_grs.head(rshiftMax-2)

  val rshift_result          = Mux(Ec_is_medium,
    rshift_result_temp,
    Mux(Ec_is_too_big, fp_c_significand_cat0.head(rshiftMax-2), 0.U((rshiftMax-2).W))
  )
  val fp_c_rshiftValue_inv_reg0 = RegEnable(Mux(is_sub.asBool ,Cat(1.U,~rshift_result),Cat(0.U,rshift_result)), fire)
  val booth_in_a = Mux(
    is_fp,
    fp_a_significand, 
    0.U
  )
  val booth_in_b = Mux(
    is_fp,
    fp_b_significand,
    0.U
  )

  val U_BoothEncoder = Module(new BoothEncoder(width = significandWidth, is_addend_expand_1bit = true))
  U_BoothEncoder.io.in_a := booth_in_a
  U_BoothEncoder.io.in_b := booth_in_b

  val U_CSAnto2 = Module(new CSA_Nto2With3to2MainPipeline(U_BoothEncoder.io.out_pp.length,U_BoothEncoder.io.out_pp.head.getWidth,pipeLevel = calcPipeLevel(U_BoothEncoder.io.out_pp.length)))
  U_CSAnto2.io.fire := fire
  U_CSAnto2.io.in := U_BoothEncoder.io.out_pp

  val CSA3to2_in_a = U_CSAnto2.io.out_sum

  // here maybe some problems
  val CSA3to2_in_b = Mux(
    is_fp_reg0,
    Cat(U_CSAnto2.io.out_car.head(significandWidth*2), is_sub_reg0 & !rshift_guard & !rshift_round & !rshift_sticky),
    0.U
  )
  val CSA3to2_in_c = Mux(
    is_fp_reg0,
    Cat(0.U,fp_c_rshiftValue_inv_reg0(2*significandWidth-1,0)),
    0.U
  )


  val U_CSA3to2 = Module(new CSA3to2(width = U_CSAnto2.io.out_sum.getWidth))
  U_CSA3to2.io.in_a := CSA3to2_in_a
  U_CSA3to2.io.in_b := CSA3to2_in_b
  U_CSA3to2.io.in_c := CSA3to2_in_c

  // mul result
  // here maybe some problems
  val adder_lowbit = U_CSA3to2.io.out_sum + U_CSA3to2.io.out_car

  val fp_c_rshift_result_high_inv_add0 = fp_c_rshiftValue_inv_reg0.head(significandWidth+3)

  val fp_c_rshift_result_high_inv_add1 = Mux(is_fp_reg0,
    Cat(0.U(3.W),fp_c_rshiftValue_inv_reg0.head(significandWidth+3)),
    0.U
  ) + 1.U(1.W)


  //
  val fp_c_rshift_result_high_inv_add1_fp = fp_c_rshift_result_high_inv_add1(significandWidth+2,0)

  val adder       = Cat(Mux(adder_lowbit.head(1).asBool, fp_c_rshift_result_high_inv_add1_fp, fp_c_rshift_result_high_inv_add0),adder_lowbit.tail(1),
    Mux(is_sub_reg0, ((~Cat(rshift_guard,rshift_round,rshift_sticky)).asUInt+1.U).head(2), Cat(rshift_guard,rshift_round))
  )

  val adder_is_negative = adder.head(1).asBool
  val adder_is_negative_reg2 = RegEnable(RegEnable(adder_is_negative, fire_reg0), fire_reg1)

  val adder_inv       = Mux(adder_is_negative, (~adder.tail(1)).asUInt, adder.tail(1))
  // ab mul is greater then c
  val Eab_is_greater    = rshift_value > 0.S
  val E_greater_reg2 = RegEnable(RegEnable(RegEnable(Mux(Eab_is_greater, Eab(exponentWidth,0).asUInt, Cat(0.U(1.W),Ec_fix)), fire), fire_reg0), fire_reg1)
  val lshift_value_max_reg0 = RegEnable(Mux(Eab_is_greater, Eab(exponentWidth,0).asUInt - 1.U, Cat(0.U,Ec_fix - 1.U)), fire)
  val LZDWidth = adder_inv.getWidth.U.getWidth

  //guard the exponent not be zero after lshift
  val lshift_value_mask = Mux(lshift_value_max_reg0.head(lshift_value_max_reg0.getWidth-LZDWidth).orR,
    0.U(adder_inv.getWidth.W),
    Fill(adder_inv.getWidth, 1.U) >> lshift_value_max_reg0.tail(lshift_value_max_reg0.getWidth-LZDWidth)
  ).asUInt

  //tail
  val tzd_adder_reg1     = LZD(RegEnable(Reverse(adder.asUInt), fire_reg0).asTypeOf(adder))
  val lzd_adder_inv_mask  = LZD(RegEnable(adder_inv | lshift_value_mask, fire_reg0).asTypeOf(adder_inv))

  val lzd_adder_inv_mask_reg1  = Wire(UInt(lzd_adder_inv_mask.getWidth.W))
  lzd_adder_inv_mask_reg1 := lzd_adder_inv_mask

  val lshift_mask_valid_reg1   = (RegEnable(adder_inv, fire_reg0) | RegEnable(lshift_value_mask, fire_reg0)) === RegEnable(lshift_value_mask, fire_reg0)
  val lshift_value_reg1        = lzd_adder_inv_mask_reg1
  val adder_reg1 = RegEnable(adder, fire_reg0)

  // left shift for norm
  val lshift_adder        = shiftLeftWithMux(adder_reg1, lshift_value_reg1)
  val lshift_adder_inv    = Cat(Mux(RegEnable(adder_is_negative, fire_reg0),~lshift_adder.head(significandWidth+4),lshift_adder.head(significandWidth+4)),lshift_adder.tail(significandWidth+4))

  val is_fix = (tzd_adder_reg1 + lzd_adder_inv_mask_reg1) === adder_inv.getWidth.U
  //after fix
  val lshift_adder_inv_fix = Mux(is_fix, lshift_adder_inv.head(adder_inv.getWidth), lshift_adder_inv.tail(1))
  val fraction_result_no_round_reg2  = RegEnable(lshift_adder_inv_fix.tail(1).head(significandWidth-1), fire_reg1)

  val fraction_result_round = fraction_result_no_round_reg2 +& 1.U

  val sign_result_temp_reg2 = RegEnable(RegEnable(Mux(adder_is_negative, RegEnable(sign_c, fire), RegEnable(sign_a_b, fire)), fire_reg0), fire_reg1)

  val RNE = io.round_mode === "b000".U
  val RTZ = io.round_mode === "b001".U
  val RDN = io.round_mode === "b010".U
  val RUP = io.round_mode === "b011".U
  val RMM = io.round_mode === "b100".U
  val RNE_reg2 = RegEnable(RegEnable(RegEnable(RNE, fire), fire_reg0), fire_reg1)
  val RTZ_reg2 = RegEnable(RegEnable(RegEnable(RTZ, fire), fire_reg0), fire_reg1)
  val RDN_reg2 = RegEnable(RegEnable(RegEnable(RDN, fire), fire_reg0), fire_reg1)
  val RUP_reg2 = RegEnable(RegEnable(RegEnable(RUP, fire), fire_reg0), fire_reg1)
  val RMM_reg2 = RegEnable(RegEnable(RegEnable(RMM, fire), fire_reg0), fire_reg1)

  val sticky_reg2  = RegEnable(RegEnable(rshift_sticky, fire_reg0) | (lzd_adder_inv_mask_reg1 + tzd_adder_reg1 < (adder_inv.getWidth-significandWidth-2).U), fire_reg1)
  val sticky_uf_reg2  = RegEnable(RegEnable(rshift_sticky, fire_reg0) | (lzd_adder_inv_mask_reg1 + tzd_adder_reg1 < (adder_inv.getWidth-significandWidth-3).U), fire_reg1)
  val round_lshift_reg2 = RegEnable(lshift_adder_inv_fix.tail(significandWidth+1).head(1), fire_reg1)
  val guard_lshift_reg2 = RegEnable(lshift_adder_inv_fix.tail(significandWidth).head(1), fire_reg1)

  val round   = Mux(adder_is_negative_reg2, round_lshift_reg2 ^ !sticky_reg2, round_lshift_reg2)
  val guard   = Mux(adder_is_negative_reg2, guard_lshift_reg2 ^ (!sticky_reg2 & round_lshift_reg2), guard_lshift_reg2)

  val guard_uf   = round
  
  val round_lshift_uf_reg2 = RegEnable(lshift_adder_inv_fix.tail(significandWidth+2).head(1), fire_reg1)
  val round_uf   = Mux(adder_is_negative_reg2, round_lshift_uf_reg2 ^ !sticky_uf_reg2, round_lshift_uf_reg2)

  val round_add1 = Wire(UInt(1.W))
  round_add1 := RNE_reg2 & (guard & (fraction_result_no_round_reg2(0) | round | sticky_reg2)) |
    RDN_reg2 & sign_result_temp_reg2 & (guard|round|sticky_reg2) |
    RUP_reg2 & !sign_result_temp_reg2 & (guard|round|sticky_reg2) |
    RMM_reg2 & guard |
    adder_is_negative_reg2 & !guard & !round & !sticky_reg2

  val round_add1_uf = RNE_reg2 & (guard_uf & (guard | round_uf | sticky_uf_reg2)) |
    RDN_reg2 & sign_result_temp_reg2 & (guard_uf|round_uf|sticky_uf_reg2) |
    RUP_reg2 & !sign_result_temp_reg2 & (guard_uf|round_uf|sticky_uf_reg2) |
    RMM_reg2 & guard_uf

  val exponent_add_1 = fraction_result_no_round_reg2.andR & round_add1.asBool

  val exponent_result_add_value = Mux(exponent_add_1 | RegEnable(is_fix, fire_reg1),
    E_greater_reg2 - RegEnable(lshift_value_reg1, fire_reg1) + 1.U,
    E_greater_reg2 - RegEnable(lshift_value_reg1, fire_reg1)
  )

  val exponent_overflow         = exponent_result_add_value.head(1).asBool | exponent_result_add_value.tail(1).andR
  val exponent_is_min           = RegEnable(!lshift_adder_inv_fix.head(1).asBool & lshift_mask_valid_reg1 & !is_fix, fire_reg1)

  val exponent_result_temp      = Mux(exponent_is_min,
    Cat(0.U((exponentWidth-1).W),exponent_add_1),
    exponent_result_add_value(exponentWidth-1,0)
  )

  val fraction_result_temp  = Mux(round_add1.asBool, fraction_result_round.tail(1), fraction_result_no_round_reg2)

  val NV,DZ,OF,UF,NX  = WireInit(false.B)

  val fflags = WireInit(Cat(NV,DZ,OF,UF,NX))

  NX := guard | round | sticky_reg2
  UF := NX & exponent_is_min & ( !exponent_add_1 | !(guard & round_add1_uf))


  val fp_a_is_zero = !io.fp_aIsFpCanonicalNAN & !fp_a_significand.orR
  val fp_b_is_zero = !io.fp_bIsFpCanonicalNAN & !fp_b_significand.orR
  val fp_c_is_zero = !io.fp_cIsFpCanonicalNAN & !fp_c_significand.orR

  val normal_result_is_zero_reg2 = RegEnable(RegEnable(!adder.orR, fire_reg0), fire_reg1)
  val has_zero_reg2 = RegEnable(RegEnable(RegEnable(fp_a_is_zero | fp_b_is_zero | fp_c_is_zero, fire), fire_reg0), fire_reg1) | normal_result_is_zero_reg2
  val normal_result = Cat(sign_result_temp_reg2,exponent_result_temp,fraction_result_temp)

  val is_overflow_reg2 = exponent_overflow

  val result_overflow_up   = Cat(sign_result_temp_reg2, Fill(exponentWidth,1.U), 0.U((significandWidth-1).W))
  val result_overflow_down = Cat(sign_result_temp_reg2, Fill(exponentWidth-1,1.U), 0.U, Fill(significandWidth-1,1.U))

  val fp_a_is_nan = io.fp_aIsFpCanonicalNAN | Ea.andR & fp_a_significand.tail(1).orR
  val fp_b_is_nan = io.fp_bIsFpCanonicalNAN | Eb.andR & fp_b_significand.tail(1).orR
  val fp_c_is_nan = io.fp_cIsFpCanonicalNAN | Ec.andR & fp_c_significand.tail(1).orR

  val has_nan = fp_a_is_nan | fp_b_is_nan | fp_c_is_nan

  val fp_a_is_snan =  !io.fp_aIsFpCanonicalNAN & Ea.andR & !fp_a_significand.tail(1).head(1) & fp_a_significand.tail(2).orR
  val fp_b_is_snan =  !io.fp_bIsFpCanonicalNAN & Eb.andR & !fp_b_significand.tail(1).head(1) & fp_b_significand.tail(2).orR
  val fp_c_is_snan =  !io.fp_cIsFpCanonicalNAN & Ec.andR & !fp_c_significand.tail(1).head(1) & fp_c_significand.tail(2).orR
  
  val has_snan = fp_a_is_snan | fp_b_is_snan | fp_c_is_snan

  val fp_a_is_inf = !io.fp_aIsFpCanonicalNAN & Ea.andR & !fp_a_significand.tail(1).orR
  val fp_b_is_inf = !io.fp_bIsFpCanonicalNAN & Eb.andR & !fp_b_significand.tail(1).orR
  val fp_c_is_inf = !io.fp_cIsFpCanonicalNAN & Ec.andR & !fp_c_significand.tail(1).orR

  val has_inf = fp_a_is_inf | fp_b_is_inf | fp_c_is_inf

  val result_inf = Cat(Fill(exponentWidth,1.U),Fill(significandWidth-1,0.U))

  val result_nan = Cat(0.U,Fill(exponentWidth+1,1.U),0.U((significandWidth-2).W))

  val fp_result = Wire(UInt(floatWidth.W))

  val has_nan_reg2       = RegEnable(RegEnable(RegEnable(has_nan, fire), fire_reg0), fire_reg1)
  val has_nan_is_NV_reg2 = RegEnable(RegEnable(RegEnable(
    has_snan.asBool | (fp_a_is_inf & fp_b_is_zero) | (fp_a_is_zero & fp_b_is_inf),
    fire), fire_reg0), fire_reg1)
  val has_inf_reg2       = RegEnable(RegEnable(RegEnable(has_inf, fire), fire_reg0), fire_reg1)
  val has_inf_is_NV_reg2 = RegEnable(RegEnable(RegEnable(
    ((fp_a_is_inf & fp_b_is_zero) | (fp_a_is_zero & fp_b_is_inf)) | (fp_c_is_inf & (fp_a_is_inf | fp_b_is_inf) & (sign_c ^ sign_a_b)),
    fire), fire_reg0), fire_reg1)
  val has_inf_result_inf_sign_reg2 = RegEnable(RegEnable(RegEnable(
    Mux(fp_a_is_inf|fp_b_is_inf,sign_a_b,sign_c),
    fire), fire_reg0), fire_reg1)
  val is_overflow_down_reg2 = RTZ_reg2 | (RDN_reg2 & !sign_result_temp_reg2.asBool) | (RUP_reg2 & sign_result_temp_reg2.asBool)
  val fp_a_or_b_is_zero_reg2 = RegEnable(RegEnable(RegEnable(fp_a_is_zero | fp_b_is_zero, fire), fire_reg0), fire_reg1)
  val fp_result_fp_a_or_b_is_zero_reg2 = RegEnable(RegEnable(RegEnable(
    Cat(
      Mux(
        fp_c_is_zero,
        Mux(is_fmul, sign_a_b, (sign_a_b & sign_c) | (RDN & (sign_a_b ^ sign_c)) ),
        fp_c.head(1)
      ),
      fp_c.tail(1)
    ),
    fire), fire_reg0), fire_reg1
  )
  when(has_nan_reg2){
    fp_result := result_nan
    fflags := Mux(has_nan_is_NV_reg2,"b10000".U,"b00000".U)
  }.elsewhen(has_inf_reg2){
    fp_result := Mux(has_inf_is_NV_reg2, result_nan, Cat(has_inf_result_inf_sign_reg2,result_inf))
    fflags := Mux(has_inf_is_NV_reg2, "b10000".U, "b00000".U)
  }.elsewhen(is_overflow_reg2){
    fp_result := Mux(is_overflow_down_reg2,result_overflow_down,result_overflow_up)
    fflags := "b00101".U
  }.elsewhen(has_zero_reg2){
    fp_result := Mux(fp_a_or_b_is_zero_reg2,
      fp_result_fp_a_or_b_is_zero_reg2,
      Mux(normal_result_is_zero_reg2, Cat(RDN_reg2,0.U((floatWidth-1).W)), normal_result)
    )
    fflags := Mux(fp_a_or_b_is_zero_reg2 | normal_result_is_zero_reg2, 0.U, Cat(NV,DZ,OF,UF,NX))
  }.otherwise{
    fp_result := normal_result
  }

  io.fp_result := Mux(
    is_fp_reg2,
    fp_result,
    0.U
  )

  io.fflags := Mux(
      is_fp_reg2,
      fflags,
      0.U
  )

}

// class CSA_Nto2With3to2MainPipeline(n :Int, width :Int, pipeLevel :Int) extends Module{
//   val io = IO(new Bundle() {
//     val fire    = Input(Bool())
//     val in      = Input(Vec(n,UInt(width.W)))
//     val out_sum = Output(UInt(width.W))
//     val out_car = Output(UInt(width.W))

//   })
//   val fire = io.fire
//   val in = ListBuffer[UInt]()
//   io.in.foreach(a => in += a)
//   val in_next = ListBuffer[UInt]()
//   var n_next = n
//   var CSA4to2Num = if(n_next==8 || n_next==4) n_next/4 else 0
//   var CSA3to2Num = if(n_next==8 || n_next==4) 0 else n_next/3
//   var remainder = n_next - CSA4to2Num*4 - CSA3to2Num*3

//   var ceng = 0
//   var is_piped = false
//   while (CSA4to2Num!=0 || CSA3to2Num!=0){
//     ceng = ceng + 1

//     if (ceng == pipeLevel) is_piped = true

//     in_next.remove(0,in_next.length)
//     for (i <- 0 until CSA4to2Num) {
//       val U_CSA4to2 = Module(new CSA4to2(width = width))
//       U_CSA4to2.io.in_a := (if (is_piped) RegEnable(in(i*4+0), fire) else in(i*4+0))
//       U_CSA4to2.io.in_b := (if (is_piped) RegEnable(in(i*4+1), fire) else in(i*4+1))
//       U_CSA4to2.io.in_c := (if (is_piped) RegEnable(in(i*4+2), fire) else in(i*4+2))
//       U_CSA4to2.io.in_d := (if (is_piped) RegEnable(in(i*4+3), fire) else in(i*4+3))
//       in_next += U_CSA4to2.io.out_sum
//       in_next += U_CSA4to2.io.out_car
//     }
//     for (i <- 0 until CSA3to2Num) {
//       val U_CSA3to2 = Module(new CSA3to2(width = width))
//       U_CSA3to2.io.in_a := (if (is_piped) RegEnable(in(i*3+0), fire) else in(i*3+0))
//       U_CSA3to2.io.in_b := (if (is_piped) RegEnable(in(i*3+1), fire) else in(i*3+1))
//       U_CSA3to2.io.in_c := (if (is_piped) RegEnable(in(i*3+2), fire) else in(i*3+2))
//       in_next += U_CSA3to2.io.out_sum
//       in_next += U_CSA3to2.io.out_car
//     }

//     if (remainder == 1) in_next += in.last
//     if (remainder == 2) {
//       in_next += in(in.length-2)
//       in_next += in.last
//     }
//     in.remove(0,in.length)
//     in_next.foreach(a => in += a)
//     n_next = (CSA4to2Num+CSA3to2Num)*2 + remainder
//     CSA4to2Num = if(n_next==8 || n_next==4) n_next/4 else 0
//     CSA3to2Num = if(n_next==8 || n_next==4) 0 else n_next/3
//     remainder = n_next - CSA4to2Num*4 - CSA3to2Num*3
//   }

//   io.out_sum := (if (pipeLevel>ceng) RegEnable(in_next(0), fire) else in_next(0))
//   io.out_car := (if (pipeLevel>ceng) RegEnable(in_next(1), fire) else in_next(1))



// }

// class CSA3to2(width :Int) extends Module{
//   val io = IO(new Bundle() {
//     val in_a   = Input(UInt(width.W))
//     val in_b   = Input(UInt(width.W))
//     val in_c   = Input(UInt(width.W))
//     val out_sum = Output(UInt(width.W))
//     val out_car = Output(UInt(width.W))

//   })
//   io.out_sum := io.in_a ^ io.in_b ^ io.in_c
//   io.out_car := Cat( ( (io.in_a & io.in_b) | (io.in_a & io.in_c) | (io.in_b & io.in_c) )(width-2,0),0.U)



// }
// class CSA4to2(width :Int) extends Module{
//   val io = IO(new Bundle() {
//     val in_a   = Input(UInt(width.W))
//     val in_b   = Input(UInt(width.W))
//     val in_c   = Input(UInt(width.W))
//     val in_d   = Input(UInt(width.W))
//     val out_sum = Output(UInt(width.W))
//     val out_car = Output(UInt(width.W))

//   })
//   val cout_vec  = Wire(Vec(width,UInt(1.W)))
//   val sum_vec   = Wire(Vec(width,UInt(1.W)))
//   val carry_vec = Wire(Vec(width,UInt(1.W)))
//   val cin = 0.U
//   for (i <- 0 until width) {
//     cout_vec(i) := Mux(io.in_a(i) ^ io.in_b(i), io.in_c(i), io.in_a(i))
//     if (i ==0){
//       sum_vec(i)   := io.in_a(i) ^ io.in_b(i) ^ io.in_c(i) ^ io.in_d(i)
//       carry_vec(i) := Mux(io.in_a(i) ^ io.in_b(i) ^ io.in_c(i) ^ io.in_d(i), cin , io.in_d(i))
//     }
//     else {
//       sum_vec(i)   :=  io.in_a(i) ^ io.in_b(i) ^ io.in_c(i) ^ io.in_d(i) ^ cout_vec(i-1)
//       carry_vec(i) := Mux(io.in_a(i) ^ io.in_b(i) ^ io.in_c(i) ^ io.in_d(i), cout_vec(i-1), io.in_d(i))
//     }
//   }

//   val sum_temp_vec   = Wire(Vec(width,UInt(1.W)))
//   val carry_temp_vec = Wire(Vec(width,UInt(1.W)))
//   carry_temp_vec(0) := 0.U
//   sum_temp_vec(0)   := sum_vec(0)
//   for (i <- 1 until width) {
//     if (i%2==1) {
//       carry_temp_vec(i) := sum_vec(i)
//       sum_temp_vec(i)   := carry_vec(i-1)
//     }
//     else {
//       carry_temp_vec(i) := carry_vec(i-1)
//       sum_temp_vec(i)   := sum_vec(i)
//     }
//   }


//   io.out_sum := sum_temp_vec.asUInt
//   io.out_car := carry_temp_vec.asUInt


// }

class BoothEncoder(
                                              width :Int = 53,
                                              is_addend_expand_1bit : Boolean = true
                                            ) extends Module{
  val addend_seq_width = if (is_addend_expand_1bit) 2*width+1 else 2*width
  val outNum  = width/2 + 1
  val io = IO(new Bundle() {
    val in_a   = Input(UInt(width.W))
    val in_b   = Input(UInt(width.W))
    val out_pp = Output(Vec(outNum,UInt(addend_seq_width.W)))
  })
  val in_b_cat = Cat(Fill(2- (width % 2),0.U),io.in_b,0.U)

  //get booth encode
  val booth_seq = Wire(Vec(outNum,UInt(3.W)))
  val booth_4bit_onehot = Wire(Vec(outNum,UInt(4.W)))
  for (i <- 0 until outNum) {
    booth_seq(i) := in_b_cat(i*2+2,i*2)
    booth_4bit_onehot(i) := 0.U
    switch(booth_seq(i)){
      is("b001".U) {booth_4bit_onehot(i) := "b1000".U}
      is("b010".U) {booth_4bit_onehot(i) := "b1000".U}
      is("b011".U) {booth_4bit_onehot(i) := "b0100".U}
      is("b100".U) {booth_4bit_onehot(i) := "b0001".U}
      is("b101".U) {booth_4bit_onehot(i) := "b0010".U}
      is("b110".U) {booth_4bit_onehot(i) := "b0010".U}
    }
  }
  //generate partial products
  val pp_seq_f16 = Wire(Vec(outNum,UInt((width+1).W)))
  val sign_seq = Wire(Vec(outNum,UInt(1.W)))
  for (i <- 0 until outNum) {
    sign_seq(i) := booth_4bit_onehot(i)(1) | booth_4bit_onehot(i)(0)
    //f16
    pp_seq_f16(i) := Fill(width + 1, booth_4bit_onehot(i)(3)) & Cat(0.U, io.in_a) |
      Fill(width + 1, booth_4bit_onehot(i)(2)) & Cat(io.in_a, 0.U) |
      Fill(width + 1, booth_4bit_onehot(i)(1)) & Cat(1.U, ~io.in_a) |
      Fill(width + 1, booth_4bit_onehot(i)(0)) & Cat(~io.in_a, 1.U)
  }
  val addend_seq_f16 = Wire(Vec(outNum,UInt(addend_seq_width.W)))
  val outNumBeforeLast = outNum-2
  val outNumLast = outNum-1
  for (i <- 0 until outNum) {
    val head_first_one_width = width - 4 - 2 * (i - 1)
    val tail_zero_width = 2 * (i - 1)
    i match {
      case 0 => addend_seq_f16(i) := Cat(0.U((width - 4).W), ~sign_seq(i), sign_seq(i), sign_seq(i), pp_seq_f16(0))
      case 1 => addend_seq_f16(i) := Cat(1.U(head_first_one_width.W), ~sign_seq(i), pp_seq_f16(i), 0.U, sign_seq(i - 1))
      case `outNumBeforeLast` =>
        if (width % 2 == 0) {
          if (is_addend_expand_1bit) addend_seq_f16(i) := Cat(1.U, ~sign_seq(i), pp_seq_f16(i), 0.U, sign_seq(i - 1), 0.U(tail_zero_width.W))
          else addend_seq_f16(i) := Cat(~sign_seq(i), pp_seq_f16(i), 0.U, sign_seq(i - 1), 0.U(tail_zero_width.W))
        }
        else addend_seq_f16(i) := Cat(1.U, ~sign_seq(i), pp_seq_f16(i), 0.U, sign_seq(i - 1), 0.U(tail_zero_width.W))
      case `outNumLast` =>
        if (width % 2 == 0) addend_seq_f16(i) := Cat(pp_seq_f16(i).tail(1), 0.U, sign_seq(i - 1), 0.U((2 * (i - 1)).W))
        else if (is_addend_expand_1bit) addend_seq_f16(i) := Cat(1.U, pp_seq_f16(i), 0.U, sign_seq(i - 1), 0.U(tail_zero_width.W))
        else addend_seq_f16(i) := Cat(pp_seq_f16(i), 0.U, sign_seq(i - 1), 0.U((2 * (i - 1)).W))
      case _ => addend_seq_f16(i) := Cat(1.U(head_first_one_width.W), ~sign_seq(i), pp_seq_f16(i), 0.U, sign_seq(i - 1), 0.U(tail_zero_width.W))
    }
  }
  io.out_pp := addend_seq_f16
}

