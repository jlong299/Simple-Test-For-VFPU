#ifndef __SIM_H__
#define __SIM_H__

#include <stdint.h>
#include <memory>

// 定义FMA操作数结构体
struct FMA_Operands {
    float a, b, c;
};

// 定义16进制FMA操作数结构体
struct FMA_Operands_Hex {
    uint32_t a_hex, b_hex, c_hex;
};
struct FMA_Operands_Hex_16 {
    uint16_t a_hex, b_hex, c_hex;
};
struct FMA_Operands_Hex_BF16 {
    uint16_t a_hex, b_hex, c_hex;
};

// 定义测试模式的枚举类型
enum class TestMode {
    FP32,
    FP16,
    BF16
};

// 定义测试结果允许误差范围
enum class ErrorType {
    Precise,
    ULP, //允许若干 ulp (unit in the last place) 的误差
    RelativeError, // 相对误差
    ULP_or_RelativeError // 允许若干 ulp 或 相对误差
};

// 前向声明Verilator相关类，避免在头文件中#include "Vtop.h"
class Vtop;
class VerilatedContext;

#ifdef VCD
class VerilatedVcdC;
#endif

// 用于从仿真器传递DUT输出到TestCase进行检查的结构体
struct DutOutputs {
    uint32_t res_out_32;
    uint16_t res_out_16_0;
    uint16_t res_out_16_1;
};

// ===================================================================
// TestCase 类: 封装单个测试用例
// ===================================================================
class TestCase {
public:
    // 构造函数 for FP32 single operation, now using the struct
    TestCase(const FMA_Operands& ops, ErrorType error_type = ErrorType::ULP);

    // 构造函数 for FP32 single operation using hexadecimal input
    TestCase(const FMA_Operands_Hex& ops_hex, ErrorType error_type = ErrorType::ULP);

    // 构造函数 for FP16 dual operation, using the struct
    TestCase(const FMA_Operands& op1, const FMA_Operands& op2, ErrorType error_type = ErrorType::ULP);
    
    // 构造函数 for FP16 dual operation using hexadecimal input
    TestCase(const FMA_Operands_Hex_16& op1, const FMA_Operands_Hex_16& op2, ErrorType error_type = ErrorType::ULP);
    
    // 构造函数 for BF16 dual operation using hexadecimal input
    TestCase(const FMA_Operands_Hex_BF16& op1, const FMA_Operands_Hex_BF16& op2, ErrorType error_type = ErrorType::ULP);
    
    void print_details() const;
    bool check_result(const DutOutputs& dut_res) const;

    TestMode mode;
    ErrorType error_type;

    // --- 公共数据成员，供 Simulator 直接访问 ---
    // 控制信号
    bool is_fp32, is_fp16, is_bf16, is_widen;

    // FP32 模式数据
    uint32_t a_fp32_bits, b_fp32_bits, c_fp32_bits;

    // FP16 模式数据
    uint16_t a1_fp16_bits, b1_fp16_bits, c1_fp16_bits;
    uint16_t a2_fp16_bits, b2_fp16_bits, c2_fp16_bits;

    // BF16 模式数据
    uint16_t a1_bf16_bits, b1_bf16_bits, c1_bf16_bits;
    uint16_t a2_bf16_bits, b2_bf16_bits, c2_bf16_bits;

private:
    // 原始浮点数值，用于打印和计算期望结果
    // FP32 - now using the struct for consistency
    FMA_Operands op_fp;
    // FP16 - uses the struct
    FMA_Operands op1_fp, op2_fp;

    // 期望结果
    uint32_t expected_res_fp32;
    uint16_t expected_res1_fp16, expected_res2_fp16;
    uint16_t expected_res1_bf16, expected_res2_bf16;
};


// ===================================================================
// Simulator 类: 封装Verilator仿真控制
// ===================================================================
class Simulator {
public:
    Simulator(int argc, char* argv[]);
    ~Simulator();

    bool run_test(const TestCase& test);
    void reset(int n);

private:
    void init_vcd();
    void single_cycle();
    
    // Verilator核心对象
    std::unique_ptr<VerilatedContext> contextp_;
    std::unique_ptr<Vtop> top_;

    // VCD波形跟踪器
#ifdef VCD
    VerilatedVcdC* tfp_ = nullptr;
#endif
};

#endif