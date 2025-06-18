// sim_c/sim.cc
#include <iostream>
#include <bitset>
#include <memory>
#include <cmath> // For std::abs

#include <verilated.h>
#include "Vtop.h"
#ifdef VCD
    #include "verilated_vcd_c.h"
#endif

#include "sim.h"
#include "fp.h"

using namespace std; 

// ===================================================================
// TestCase 实现
// ===================================================================

// FP32 single operation constructor
TestCase::TestCase(const FMA_Operands& ops, ErrorType error_type) 
    : mode(TestMode::FP32), 
      error_type(error_type),
      op_fp(ops),
      is_fp32(true), is_fp16(false), is_bf16(false), is_widen(false)
{
    memcpy(&a_fp32_bits, &op_fp.a, sizeof(uint32_t));
    memcpy(&b_fp32_bits, &op_fp.b, sizeof(uint32_t));
    memcpy(&c_fp32_bits, &op_fp.c, sizeof(uint32_t));

    float expected_fp = op_fp.a * op_fp.b + op_fp.c;
    memcpy(&expected_res_fp32, &expected_fp, sizeof(uint32_t));
}

// FP32 single operation constructor using hexadecimal input
TestCase::TestCase(const FMA_Operands_Hex& ops_hex, ErrorType error_type) 
    : mode(TestMode::FP32), 
      error_type(error_type),
      is_fp32(true), is_fp16(false), is_bf16(false), is_widen(false)
{
    // 直接使用16进制值
    a_fp32_bits = ops_hex.a_hex;
    b_fp32_bits = ops_hex.b_hex;
    c_fp32_bits = ops_hex.c_hex;

    // 将16进制位模式转换为浮点数用于打印和计算
    memcpy(&op_fp.a, &a_fp32_bits, sizeof(float));
    memcpy(&op_fp.b, &b_fp32_bits, sizeof(float));
    memcpy(&op_fp.c, &c_fp32_bits, sizeof(float));

    // 计算期望结果
    float expected_fp = op_fp.a * op_fp.b + op_fp.c;
    memcpy(&expected_res_fp32, &expected_fp, sizeof(uint32_t));
}

// FP16 dual operation constructor
TestCase::TestCase(const FMA_Operands_Hex_16& op1, const FMA_Operands_Hex_16& op2, ErrorType error_type)
    : mode(TestMode::FP16),
      error_type(error_type),
      is_fp32(false), is_fp16(true), is_bf16(false), is_widen(false)
{
    // Convert and store bits for operand set 1
    a1_fp16_bits = op1.a_hex;
    b1_fp16_bits = op1.b_hex;
    c1_fp16_bits = op1.c_hex;

    // Convert and store bits for operand set 2
    a2_fp16_bits = op2.a_hex;
    b2_fp16_bits = op2.b_hex;
    c2_fp16_bits = op2.c_hex;

    // Convert FP16 hex values to FP32 for calculation
    op1_fp.a = fp16_to_fp32(op1.a_hex);
    op1_fp.b = fp16_to_fp32(op1.b_hex);
    op1_fp.c = fp16_to_fp32(op1.c_hex);
    
    op2_fp.a = fp16_to_fp32(op2.a_hex);
    op2_fp.b = fp16_to_fp32(op2.b_hex);
    op2_fp.c = fp16_to_fp32(op2.c_hex);

    // Calculate and store expected results with FP16 overflow handling
    // For operand set 1
    float mult_result1 = op1_fp.a * op1_fp.b;
    uint16_t mult_fp16_1 = fp32_to_fp16(mult_result1);
    uint16_t c_fp16_1 = fp32_to_fp16(op1_fp.c);
    float expected_fp1;
    
    // Check if multiplication result is infinity in FP16
    if ((mult_fp16_1 & 0x7C00) == 0x7C00 && (mult_fp16_1 & 0x03FF) == 0) {
        // If a*b is infinity, final result is a*b (ignore c)
        expected_fp1 = fp16_to_fp32(mult_fp16_1);
    } else if ((c_fp16_1 & 0x7C00) == 0x7C00 && (c_fp16_1 & 0x03FF) == 0) {
        // Otherwise, if c is infinity, final result is c (ignore a*b)
        expected_fp1 = fp16_to_fp32(c_fp16_1);
    } else {
        // Normal case: perform a*b + c
        expected_fp1 = mult_result1 + op1_fp.c;
    }
    
    // For operand set 2
    float mult_result2 = op2_fp.a * op2_fp.b;
    uint16_t mult_fp16_2 = fp32_to_fp16(mult_result2);
    uint16_t c_fp16_2 = fp32_to_fp16(op2_fp.c);
    float expected_fp2;

    // Check if multiplication result is infinity in FP16
    if ((mult_fp16_2 & 0x7C00) == 0x7C00 && (mult_fp16_2 & 0x03FF) == 0) {
        // If a*b is infinity, final result is a*b (ignore c)
        expected_fp2 = fp16_to_fp32(mult_fp16_2);
    } else if ((c_fp16_2 & 0x7C00) == 0x7C00 && (c_fp16_2 & 0x03FF) == 0) {
        // Otherwise, if c is infinity, final result is c (ignore a*b)
        expected_fp2 = fp16_to_fp32(c_fp16_2);
    } else {
        // Normal case: perform a*b + c
        expected_fp2 = mult_result2 + op2_fp.c;
    }
    
    expected_res1_fp16 = fp32_to_fp16(expected_fp1);
    expected_res2_fp16 = fp32_to_fp16(expected_fp2);
}

// BF16 dual operation constructor
TestCase::TestCase(const FMA_Operands_Hex_BF16& op1, const FMA_Operands_Hex_BF16& op2, ErrorType error_type)
    : mode(TestMode::BF16),
      error_type(error_type),
      is_fp32(false), is_fp16(false), is_bf16(true), is_widen(false)
{
    // Convert and store bits for operand set 1
    a1_bf16_bits = op1.a_hex;
    b1_bf16_bits = op1.b_hex;
    c1_bf16_bits = op1.c_hex;

    // Convert and store bits for operand set 2
    a2_bf16_bits = op2.a_hex;
    b2_bf16_bits = op2.b_hex;
    c2_bf16_bits = op2.c_hex;

    // Convert BF16 hex values to FP32 for calculation
    op1_fp.a = bf16_to_fp32(op1.a_hex);
    op1_fp.b = bf16_to_fp32(op1.b_hex);
    op1_fp.c = bf16_to_fp32(op1.c_hex);
    
    op2_fp.a = bf16_to_fp32(op2.a_hex);
    op2_fp.b = bf16_to_fp32(op2.b_hex);
    op2_fp.c = bf16_to_fp32(op2.c_hex);

    // Calculate and store expected results with BF16 overflow handling
    // For operand set 1
    float mult_result1 = op1_fp.a * op1_fp.b;
    uint16_t mult_bf16_1 = fp32_to_bf16(mult_result1);
    uint16_t c_bf16_1 = fp32_to_bf16(op1_fp.c);
    float expected_fp1;
    
    // Check if multiplication result is infinity in BF16
    if ((mult_bf16_1 & 0x7F80) == 0x7F80 && (mult_bf16_1 & 0x007F) == 0) {
        // If a*b is infinity, final result is a*b (ignore c)
        expected_fp1 = bf16_to_fp32(mult_bf16_1);
    } else if ((c_bf16_1 & 0x7F80) == 0x7F80 && (c_bf16_1 & 0x007F) == 0) {
        // Otherwise, if c is infinity, final result is c (ignore a*b)
        expected_fp1 = bf16_to_fp32(c_bf16_1);
    } else {
        // Normal case: perform a*b + c
        expected_fp1 = mult_result1 + op1_fp.c;
    }
    
    // For operand set 2
    float mult_result2 = op2_fp.a * op2_fp.b;
    uint16_t mult_bf16_2 = fp32_to_bf16(mult_result2);
    uint16_t c_bf16_2 = fp32_to_bf16(op2_fp.c);
    float expected_fp2;

    // Check if multiplication result is infinity in BF16
    if ((mult_bf16_2 & 0x7F80) == 0x7F80 && (mult_bf16_2 & 0x007F) == 0) {
        // If a*b is infinity, final result is a*b (ignore c)
        expected_fp2 = bf16_to_fp32(mult_bf16_2);
    } else if ((c_bf16_2 & 0x7F80) == 0x7F80 && (c_bf16_2 & 0x007F) == 0) {
        // Otherwise, if c is infinity, final result is c (ignore a*b)
        expected_fp2 = bf16_to_fp32(c_bf16_2);
    } else {
        // Normal case: perform a*b + c
        expected_fp2 = mult_result2 + op2_fp.c;
    }
    
    expected_res1_bf16 = fp32_to_bf16(expected_fp1);
    expected_res2_bf16 = fp32_to_bf16(expected_fp2);
}


void TestCase::print_details() const {
    printf("--- Test Case ---\n");
    switch(mode) {
        case TestMode::FP32:
            printf("Mode: FP32 Single (Hex Input)\n");
            printf("Inputs (HEX): a=0x%08X, b=0x%08X, c=0x%08X\n", 
                   a_fp32_bits, b_fp32_bits, c_fp32_bits);
            printf("Inputs (FP):  a=%.8f, b=%.8f, c=%.8f\n", 
                   op_fp.a, op_fp.b, op_fp.c);
            float expected_fp;
            memcpy(&expected_fp, &expected_res_fp32, sizeof(float));
            printf("Expected: %.8f (HEX: 0x%08X)\n", expected_fp, expected_res_fp32);
            break;
        case TestMode::FP16:
            printf("Mode: FP16 Dual\n");
            printf("Inputs OP1: a=%.8f (0x%x), b=%.8f (0x%x), c=%.8f (0x%x)\n", 
                   op1_fp.a, a1_fp16_bits, 
                   op1_fp.b, b1_fp16_bits, 
                   op1_fp.c, c1_fp16_bits);
            printf("Inputs OP2: a=%.8f (0x%x), b=%.8f (0x%x), c=%.8f (0x%x)\n", 
                   op2_fp.a, a2_fp16_bits, 
                   op2_fp.b, b2_fp16_bits, 
                   op2_fp.c, c2_fp16_bits);
            printf("Expected1: %.8f (HEX: 0x%x)\n", fp16_to_fp32(expected_res1_fp16), expected_res1_fp16);
            printf("Expected2: %.8f (HEX: 0x%x)\n", fp16_to_fp32(expected_res2_fp16), expected_res2_fp16);
            break;
        case TestMode::BF16:
            printf("Mode: BF16 Dual\n");
            printf("Inputs OP1: a=%.8f (0x%x), b=%.8f (0x%x), c=%.8f (0x%x)\n", 
                   op1_fp.a, a1_bf16_bits, 
                   op1_fp.b, b1_bf16_bits, 
                   op1_fp.c, c1_bf16_bits);
            printf("Inputs OP2: a=%.8f (0x%x), b=%.8f (0x%x), c=%.8f (0x%x)\n", 
                   op2_fp.a, a2_bf16_bits, 
                   op2_fp.b, b2_bf16_bits, 
                   op2_fp.c, c2_bf16_bits);
            printf("Expected1: %.8f (HEX: 0x%x)\n", bf16_to_fp32(expected_res1_bf16), expected_res1_bf16);
            printf("Expected2: %.8f (HEX: 0x%x)\n", bf16_to_fp32(expected_res2_bf16), expected_res2_bf16);
            break;
    }
}

bool TestCase::check_result(const DutOutputs& dut_res) const {
    printf("--- Verification ---\n");
    
    // 辅助函数：检查两个FP32数是否都是零（忽略符号位）
    auto both_fp32_zero = [](uint32_t a, uint32_t b) {
        return ((a & 0x7FFFFFFF) == 0) && ((b & 0x7FFFFFFF) == 0);
    };
    
    // 辅助函数：检查两个FP/BF16数是否都是零（忽略符号位）
    auto both_f16_zero = [](uint16_t a, uint16_t b) {
        return ((a & 0x7FFF) == 0) && ((b & 0x7FFF) == 0);
    };
    
    bool pass = false;
    switch(mode) {
        case TestMode::FP32: {
            float dut_res_fp;
            memcpy(&dut_res_fp, &dut_res.res_out_32, sizeof(float));
            printf("DUT Result: %.8f (HEX: 0x%08X)\n", dut_res_fp, dut_res.res_out_32);
            float expected_fp;
            memcpy(&expected_fp, &expected_res_fp32, sizeof(float));
            int64_t ulp_diff = 0;
            float relative_error = 0;

            bool precise_pass = (dut_res.res_out_32 == expected_res_fp32);
            
            // 如果两个数都是0（忽略符号位），认为通过
            bool both_zero = both_fp32_zero(dut_res.res_out_32, expected_res_fp32);
            
            if (error_type == ErrorType::Precise) {
                pass = precise_pass || both_zero;
            }
            if (error_type == ErrorType::ULP) {
                // 允许8 ulp (unit in the last place) 的误差
                ulp_diff = std::abs((int64_t)dut_res.res_out_32 - (int64_t)expected_res_fp32);
                pass = (ulp_diff <= 8) || both_zero;
            }
            if (error_type == ErrorType::RelativeError) {
                float max_abs = std::max(std::abs(op_fp.a * op_fp.b), std::abs(op_fp.c));
                relative_error = std::abs(dut_res_fp - expected_fp) / max_abs;
                pass = ((max_abs < std::pow(2, -60)) 
                       ? (relative_error < 1e-3) //若ab或c的绝对值太小，则放宽误差要求
                       : (relative_error < 1e-5)) 
                       || precise_pass || both_zero;
            }
            if (!pass) {
                if (error_type == ErrorType::Precise) {
                    printf("ERROR: Expected 0x%08X, Got 0x%08X (Exact match required)\n", 
                           expected_res_fp32, dut_res.res_out_32);
                }
                if (error_type == ErrorType::ULP) {
                    printf("ERROR: Expected 0x%08X, Got 0x%08X, ULP diff: %ld\n", 
                           expected_res_fp32, dut_res.res_out_32, ulp_diff);
                }
                if (error_type == ErrorType::RelativeError) {
                    printf("ERROR: Expected 0x%08X, Got 0x%08X, Relative Error: %f\n", 
                           expected_res_fp32, dut_res.res_out_32, relative_error);
                }
            }
            if (error_type == ErrorType::ULP) {
                printf("ULP diff: %ld\n", ulp_diff);
            }
            if (error_type == ErrorType::RelativeError) {
                printf("Relative diff ratio: %.8e\n", relative_error);
            }
            break;
        }
        case TestMode::FP16: {
            printf("DUT Result1: %.4f (HEX: 0x%x)\n", fp16_to_fp32(dut_res.res_out_16_0), dut_res.res_out_16_0);
            printf("DUT Result2: %.4f (HEX: 0x%x)\n", fp16_to_fp32(dut_res.res_out_16_1), dut_res.res_out_16_1);

            bool pass1 = false, pass2 = false;
            
            // 检查两个数是否都是0（忽略符号位）
            bool both_zero1 = both_f16_zero(dut_res.res_out_16_0, expected_res1_fp16);
            bool both_zero2 = both_f16_zero(dut_res.res_out_16_1, expected_res2_fp16);
            
            if (error_type == ErrorType::Precise) {
                // 精确匹配
                pass1 = (dut_res.res_out_16_0 == expected_res1_fp16) || both_zero1;
                pass2 = (dut_res.res_out_16_1 == expected_res2_fp16) || both_zero2;
            } else if (error_type == ErrorType::ULP) {
                // 允许ULP误差（FP16允许2 ULP误差）
                int32_t ulp_diff1 = std::abs((int32_t)dut_res.res_out_16_0 - (int32_t)expected_res1_fp16);
                int32_t ulp_diff2 = std::abs((int32_t)dut_res.res_out_16_1 - (int32_t)expected_res2_fp16);
                pass1 = (ulp_diff1 <= 2) || both_zero1;
                pass2 = (ulp_diff2 <= 2) || both_zero2;
                
                if (!pass1) {
                    printf("ERROR OP1: Expected 0x%x, Got 0x%x, ULP diff: %d\n", 
                           expected_res1_fp16, dut_res.res_out_16_0, ulp_diff1);
                }
                if (!pass2) {
                    printf("ERROR OP2: Expected 0x%x, Got 0x%x, ULP diff: %d\n", 
                           expected_res2_fp16, dut_res.res_out_16_1, ulp_diff2);
                }
                printf("ULP diff1: %d, ULP diff2: %d\n", ulp_diff1, ulp_diff2);
            } else if (error_type == ErrorType::RelativeError) {
                // 相对误差检查（FP16）
                float dut_res1_fp = fp16_to_fp32(dut_res.res_out_16_0);
                float dut_res2_fp = fp16_to_fp32(dut_res.res_out_16_1);
                float expected1_fp = fp16_to_fp32(expected_res1_fp16);
                float expected2_fp = fp16_to_fp32(expected_res2_fp16);
                
                // 计算操作数1的相对误差
                float max_abs1 = std::max(std::abs(op1_fp.a * op1_fp.b), std::abs(op1_fp.c));
                float relative_error1 = std::abs(dut_res1_fp - expected1_fp) / max_abs1;
                bool precise_pass1 = (dut_res.res_out_16_0 == expected_res1_fp16);
                pass1 = ((max_abs1 < std::pow(2, -10))  // FP16精度较低，调整阈值
                        ? (relative_error1 < 1e-2)      // 若ab或c的绝对值太小，则放宽误差要求
                        : (relative_error1 < 1e-3))     // FP16相对误差要求比FP32宽松
                        || precise_pass1 || both_zero1;
                
                // 计算操作数2的相对误差
                float max_abs2 = std::max(std::abs(op2_fp.a * op2_fp.b), std::abs(op2_fp.c));
                float relative_error2 = std::abs(dut_res2_fp - expected2_fp) / max_abs2;
                bool precise_pass2 = (dut_res.res_out_16_1 == expected_res2_fp16);
                pass2 = ((max_abs2 < std::pow(2, -10))  // FP16精度较低，调整阈值
                        ? (relative_error2 < 1e-2)      // 若ab或c的绝对值太小，则放宽误差要求
                        : (relative_error2 < 1e-3))     // FP16相对误差要求比FP32宽松
                        || precise_pass2 || both_zero2;
                
                if (!pass1) {
                    printf("ERROR OP1: Expected 0x%x (%.4f), Got 0x%x (%.4f), Relative Error: %e\n", 
                           expected_res1_fp16, expected1_fp, dut_res.res_out_16_0, dut_res1_fp, relative_error1);
                }
                if (!pass2) {
                    printf("ERROR OP2: Expected 0x%x (%.4f), Got 0x%x (%.4f), Relative Error: %e\n", 
                           expected_res2_fp16, expected2_fp, dut_res.res_out_16_1, dut_res2_fp, relative_error2);
                }
                printf("Relative error1: %.6e, Relative error2: %.6e\n", relative_error1, relative_error2);
            }
            
            pass = pass1 && pass2;
            break;
        }
        case TestMode::BF16: {
            printf("DUT Result1: %.4f (HEX: 0x%x)\n", bf16_to_fp32(dut_res.res_out_16_0), dut_res.res_out_16_0);
            printf("DUT Result2: %.4f (HEX: 0x%x)\n", bf16_to_fp32(dut_res.res_out_16_1), dut_res.res_out_16_1);

            bool pass1 = false, pass2 = false;
            
            // 检查两个数是否都是0（忽略符号位）
            bool both_zero1 = both_f16_zero(dut_res.res_out_16_0, expected_res1_bf16);
            bool both_zero2 = both_f16_zero(dut_res.res_out_16_1, expected_res2_bf16);
            
            // 先计算ULP和RelativeError下的通过情况
            bool ulp_pass1 = false, ulp_pass2 = false;
            bool rel_pass1 = false, rel_pass2 = false;
            
            // ULP误差计算（BF16允许2 ULP误差）
            int32_t ulp_diff1 = std::abs((int32_t)dut_res.res_out_16_0 - (int32_t)expected_res1_bf16);
            int32_t ulp_diff2 = std::abs((int32_t)dut_res.res_out_16_1 - (int32_t)expected_res2_bf16);
            ulp_pass1 = (ulp_diff1 <= 2) || both_zero1;
            ulp_pass2 = (ulp_diff2 <= 2) || both_zero2;
            
            // 相对误差计算（BF16）
            float dut_res1_fp = bf16_to_fp32(dut_res.res_out_16_0);
            float dut_res2_fp = bf16_to_fp32(dut_res.res_out_16_1);
            float expected1_fp = bf16_to_fp32(expected_res1_bf16);
            float expected2_fp = bf16_to_fp32(expected_res2_bf16);
            
            // 计算操作数1的相对误差
            float max_abs1 = std::max(std::abs(op1_fp.a * op1_fp.b), std::abs(op1_fp.c));
            float relative_error1 = std::abs(dut_res1_fp - expected1_fp) / max_abs1;
            bool precise_pass1 = (dut_res.res_out_16_0 == expected_res1_bf16);
            rel_pass1 = ((max_abs1 < std::pow(2, -30))  // BF16有较好的指数范围，但尾数精度较低
                    ? (relative_error1 < 1e-2)      // 若ab或c的绝对值太小，则放宽误差要求
                    : (relative_error1 < 8e-3))     // BF16相对误差要求介于FP32和FP16之间
                    || precise_pass1 || both_zero1;
            
            // 计算操作数2的相对误差
            float max_abs2 = std::max(std::abs(op2_fp.a * op2_fp.b), std::abs(op2_fp.c));
            float relative_error2 = std::abs(dut_res2_fp - expected2_fp) / max_abs2;
            bool precise_pass2 = (dut_res.res_out_16_1 == expected_res2_bf16);
            rel_pass2 = ((max_abs2 < std::pow(2, -30))  // BF16有较好的指数范围，但尾数精度较低
                    ? (relative_error2 < 1e-2)      // 若ab或c的绝对值太小，则放宽误差要求
                    : (relative_error2 < 8e-3))     // BF16相对误差要求介于FP32和FP16之间
                    || precise_pass2 || both_zero2;
            
            // 根据错误类型决定最终的通过条件
            if (error_type == ErrorType::Precise) {
                // 精确匹配
                pass1 = (dut_res.res_out_16_0 == expected_res1_bf16) || both_zero1;
                pass2 = (dut_res.res_out_16_1 == expected_res2_bf16) || both_zero2;
            } else if (error_type == ErrorType::ULP) {
                // 只使用ULP误差
                pass1 = ulp_pass1;
                pass2 = ulp_pass2;
                
                if (!pass1) {
                    printf("ERROR OP1: Expected 0x%x, Got 0x%x, ULP diff: %d\n", 
                           expected_res1_bf16, dut_res.res_out_16_0, ulp_diff1);
                }
                if (!pass2) {
                    printf("ERROR OP2: Expected 0x%x, Got 0x%x, ULP diff: %d\n", 
                           expected_res2_bf16, dut_res.res_out_16_1, ulp_diff2);
                }
                printf("ULP diff1: %d, ULP diff2: %d\n", ulp_diff1, ulp_diff2);
            } else if (error_type == ErrorType::RelativeError) {
                // 只使用相对误差
                pass1 = rel_pass1;
                pass2 = rel_pass2;
                
                if (!pass1) {
                    printf("ERROR OP1: Expected 0x%x (%.4f), Got 0x%x (%.4f), Relative Error: %e\n", 
                           expected_res1_bf16, expected1_fp, dut_res.res_out_16_0, dut_res1_fp, relative_error1);
                }
                if (!pass2) {
                    printf("ERROR OP2: Expected 0x%x (%.4f), Got 0x%x (%.4f), Relative Error: %e\n", 
                           expected_res2_bf16, expected2_fp, dut_res.res_out_16_1, dut_res2_fp, relative_error2);
                }
                printf("Relative error1: %.6e, Relative error2: %.6e\n", relative_error1, relative_error2);
            } else if (error_type == ErrorType::ULP_or_RelativeError) {
                // ULP或相对误差：如果ULP通过则通过，否则如果相对误差通过则通过，否则不通过
                pass1 = ulp_pass1 || rel_pass1;
                pass2 = ulp_pass2 || rel_pass2;
                
                printf("ULP pass1: %s, ULP pass2: %s\n", ulp_pass1 ? "true" : "false", ulp_pass2 ? "true" : "false");
                printf("Rel pass1: %s, Rel pass2: %s\n", rel_pass1 ? "true" : "false", rel_pass2 ? "true" : "false");
                printf("ULP diff1: %d, ULP diff2: %d\n", ulp_diff1, ulp_diff2);
                printf("Relative error1: %.6e, Relative error2: %.6e\n", relative_error1, relative_error2);
                
                if (!pass1) {
                    printf("ERROR OP1: Expected 0x%x (%.4f), Got 0x%x (%.4f), ULP diff: %d, Relative Error: %e\n", 
                           expected_res1_bf16, expected1_fp, dut_res.res_out_16_0, dut_res1_fp, ulp_diff1, relative_error1);
                }
                if (!pass2) {
                    printf("ERROR OP2: Expected 0x%x (%.4f), Got 0x%x (%.4f), ULP diff: %d, Relative Error: %e\n", 
                           expected_res2_bf16, expected2_fp, dut_res.res_out_16_1, dut_res2_fp, ulp_diff2, relative_error2);
                }
            }
            
            pass = pass1 && pass2;
            break;
        }
    }

    if (pass) {
        printf(">>> PASSED <<<\n\n");
    } else {
        printf(">>> FAILED <<<\n\n");
    }
    return pass;
}

// ===================================================================
// Simulator 实现
// ===================================================================
Simulator::Simulator(int argc, char* argv[]) {
    contextp_ = std::make_unique<VerilatedContext>();
    top_ = std::make_unique<Vtop>(contextp_.get(), "TOP");
    Verilated::commandArgs(argc, argv);
#ifdef VCD
    init_vcd();
#endif
}

Simulator::~Simulator() {
    top_->final();
#if VCD
    if (tfp_) {
        tfp_->close();
        tfp_ = nullptr;
    }
#endif
}

void Simulator::init_vcd() {
#ifdef VCD
    contextp_->traceEverOn(true);
    tfp_ = new VerilatedVcdC;
    top_->trace(tfp_, 99);
    tfp_->open("build/fma/top.vcd");
#endif
}

void Simulator::single_cycle() {
    contextp_->timeInc(5);
    top_->clock = 0; top_->eval();
#ifdef VCD
    if (tfp_) tfp_->dump(contextp_->time());
#endif

    contextp_->timeInc(5);
    top_->clock = 1; top_->eval();
#ifdef VCD
    if (tfp_) tfp_->dump(contextp_->time());
#endif
}

void Simulator::reset(int n) {
    top_->reset = 1;
    while (n-- > 0) single_cycle();
    top_->reset = 0;
    top_->eval();
}

bool Simulator::run_test(const TestCase& test) {
    // 1. 设置控制信号
    top_->io_valid_in = 1;
    top_->io_is_fp32  = test.is_fp32;
    top_->io_is_fp16  = test.is_fp16;
    top_->io_is_bf16  = test.is_bf16;
    top_->io_is_widen = test.is_widen;

    // 2. 根据模式设置数据输入端口
    switch(test.mode) {
        case TestMode::FP32:
            top_->io_a_in_32 = test.a_fp32_bits;
            top_->io_b_in_32 = test.b_fp32_bits;
            top_->io_c_in_32 = test.c_fp32_bits;
            break;
        case TestMode::FP16:
            // 注意：Verilator会把 a_in_16: Vec(2, UInt(16.W)) 转换成 io_a_in_16_0, io_a_in_16_1
            top_->io_a_in_16_0 = test.a1_fp16_bits;
            top_->io_b_in_16_0 = test.b1_fp16_bits;
            top_->io_c_in_16_0 = test.c1_fp16_bits;
            top_->io_a_in_16_1 = test.a2_fp16_bits;
            top_->io_b_in_16_1 = test.b2_fp16_bits;
            top_->io_c_in_16_1 = test.c2_fp16_bits;
            break;
        case TestMode::BF16:
            // BF16也使用16位端口
            top_->io_a_in_16_0 = test.a1_bf16_bits;
            top_->io_b_in_16_0 = test.b1_bf16_bits;
            top_->io_c_in_16_0 = test.c1_bf16_bits;
            top_->io_a_in_16_1 = test.a2_bf16_bits;
            top_->io_b_in_16_1 = test.b2_bf16_bits;
            top_->io_c_in_16_1 = test.c2_bf16_bits;
            break;
    }
    
    test.print_details();
    single_cycle();
    
    int max_cycles = 20;
    while (max_cycles-- > 0) {
        if (top_->io_valid_out) {
            single_cycle();
            break;
        } else {
            top_->io_valid_in = 0;
            single_cycle();
        }
    }
    
    // 3. 从DUT读取所有可能的输出
    DutOutputs dut_res;
    dut_res.res_out_32   = top_->io_res_out_32;
    dut_res.res_out_16_0 = top_->io_res_out_16_0;
    dut_res.res_out_16_1 = top_->io_res_out_16_1;
    
    // 4. TestCase自己负责检查并返回结果
    return test.check_result(dut_res);
}