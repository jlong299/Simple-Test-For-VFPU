// sim_c/sim.cc
#include <iostream>
#include <bitset>
#include <memory>

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
TestCase::TestCase(const FMA_Operands& ops) 
    : mode(TestMode::FP32), 
      op_fp(ops),
      is_fp32(true), is_fp16(false), is_bf16(false), is_widen(false)
{
    memcpy(&a_fp32_bits, &op_fp.a, sizeof(uint32_t));
    memcpy(&b_fp32_bits, &op_fp.b, sizeof(uint32_t));
    memcpy(&c_fp32_bits, &op_fp.c, sizeof(uint32_t));

    float expected_fp = op_fp.a * op_fp.b + op_fp.c;
    memcpy(&expected_res_fp32, &expected_fp, sizeof(uint32_t));
}

// FP16 dual operation constructor
TestCase::TestCase(const FMA_Operands& op1, const FMA_Operands& op2)
    : mode(TestMode::FP16),
      op1_fp(op1), op2_fp(op2),
      is_fp32(false), is_fp16(true), is_bf16(false), is_widen(false)
{
    // Convert and store bits for operand set 1
    a1_fp16_bits = fp32_to_fp16(op1_fp.a);
    b1_fp16_bits = fp32_to_fp16(op1_fp.b);
    c1_fp16_bits = fp32_to_fp16(op1_fp.c);

    // Convert and store bits for operand set 2
    a2_fp16_bits = fp32_to_fp16(op2_fp.a);
    b2_fp16_bits = fp32_to_fp16(op2_fp.b);
    c2_fp16_bits = fp32_to_fp16(op2_fp.c);

    // Calculate and store expected results
    float expected_fp1 = op1_fp.a * op1_fp.b + op1_fp.c;
    float expected_fp2 = op2_fp.a * op2_fp.b + op2_fp.c;
    expected_res1_fp16 = fp32_to_fp16(expected_fp1);
    expected_res2_fp16 = fp32_to_fp16(expected_fp2);
}


void TestCase::print_details() const {
    printf("--- Test Case ---\n");
    switch(mode) {
        case TestMode::FP32:
            printf("Mode: FP32 Single\n");
            printf("Inputs: a=%.4f, b=%.4f, c=%.4f\n", op_fp.a, op_fp.b, op_fp.c);
            float expected_fp;
            memcpy(&expected_fp, &expected_res_fp32, sizeof(float));
            printf("Expected: %.4f (HEX: 0x%x)\n", expected_fp, expected_res_fp32);
            break;
        case TestMode::FP16:
            printf("Mode: FP16 Dual\n");
            printf("Inputs OP1: a=%.4f (0x%x), b=%.4f (0x%x), c=%.4f (0x%x)\n", 
                   op1_fp.a, a1_fp16_bits, 
                   op1_fp.b, b1_fp16_bits, 
                   op1_fp.c, c1_fp16_bits);
            printf("Inputs OP2: a=%.4f (0x%x), b=%.4f (0x%x), c=%.4f (0x%x)\n", 
                   op2_fp.a, a2_fp16_bits, 
                   op2_fp.b, b2_fp16_bits, 
                   op2_fp.c, c2_fp16_bits);
            printf("Expected1: %.4f (HEX: 0x%x)\n", half_to_float(expected_res1_fp16), expected_res1_fp16);
            printf("Expected2: %.4f (HEX: 0x%x)\n", half_to_float(expected_res2_fp16), expected_res2_fp16);
            break;
    }
}

bool TestCase::check_result(const DutOutputs& dut_res) const {
    printf("--- Verification ---\n");
    bool pass = false;
    switch(mode) {
        case TestMode::FP32: {
            float dut_res_fp;
            memcpy(&dut_res_fp, &dut_res.res_out_32, sizeof(float));
            printf("DUT Result: %.4f (HEX: 0x%x)\n", dut_res_fp, dut_res.res_out_32);
            pass = (dut_res.res_out_32 == expected_res_fp32);
            break;
        }
        case TestMode::FP16: {
            printf("DUT Result1: %.4f (HEX: 0x%x)\n", half_to_float(dut_res.res_out_16_0), dut_res.res_out_16_0);
            printf("DUT Result2: %.4f (HEX: 0x%x)\n", half_to_float(dut_res.res_out_16_1), dut_res.res_out_16_1);
            pass = (dut_res.res_out_16_0 == expected_res1_fp16) && (dut_res.res_out_16_1 == expected_res2_fp16);
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

void Simulator::run_test(const TestCase& test) {
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
    
    // 4. TestCase自己负责检查
    test.check_result(dut_res);
}