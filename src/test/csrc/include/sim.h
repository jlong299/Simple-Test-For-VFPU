#ifndef __SIM_H__
#define __SIM_H__

#include <stdint.h>
#include <memory>

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
    TestCase(float a, float b, float c);
    void print_details() const;
    bool check_result(uint32_t dut_res) const;

    uint32_t a_bits, b_bits, c_bits;

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

private:
    void init_vcd();
    void single_cycle();
    void reset(int n);
    
    // Verilator核心对象
    std::unique_ptr<VerilatedContext> contextp_;
    std::unique_ptr<Vtop> top_;

    // VCD波形跟踪器
#ifdef VCD
    VerilatedVcdC* tfp_ = nullptr;
#endif
};


// ===================================================================
// 旧的函数声明 (将被移除或设为私有)
// ===================================================================
// void sim_main(int argc, char *argv[]);

#endif

//---- Old code of reduction ----
// typedef struct {
//     uint32_t vs1[VLEN/XLEN];
//     uint32_t vs2[VLEN/XLEN];
//     uint32_t vd;
//     uint32_t expected_vd;
//     uint32_t is_vfredsum;
//     uint32_t is_vfredmax;
//     uint32_t vlmul;
//     uint32_t round_mode;
//     uint32_t fp_format;
//     uint32_t is_vec;
//     uint32_t index;
// } IOput;