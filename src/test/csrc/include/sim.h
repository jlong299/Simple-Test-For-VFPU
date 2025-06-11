#ifndef __SIM_H__
#define __SIM_H__

#include <stdint.h>
#include <memory>

// 定义浮点数格式的枚举类型
enum class FpType {
    FP32,
    FP16,
    BF16
};

// 定义加宽模式的枚举类型
enum class Widen {
    Yes,
    No
};

// 前向声明Verilator相关类，避免在头文件中#include "Vtop.h"
class Vtop;
class VerilatedContext;

#ifdef VCD
class VerilatedVcdC;
#endif

// ===================================================================
// TestCase 类: 封装单个测试用例
// ===================================================================
class TestCase {
public:
    TestCase(FpType format, Widen widen, float a, float b, float c);
    void print_details() const;
    bool check_result(uint32_t dut_res) const;

    uint32_t a_bits, b_bits, c_bits;
    // 控制信号，由构造函数根据枚举自动生成
    bool is_fp32, is_fp16, is_bf16, is_widen;

private:
    float a_fp, b_fp, c_fp;
    uint32_t expected_res_bits;
};


// ===================================================================
// Simulator 类: 封装Verilator仿真控制
// ===================================================================
class Simulator {
public:
    Simulator(int argc, char* argv[]);
    ~Simulator();

    void run_test(const TestCase& test);
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