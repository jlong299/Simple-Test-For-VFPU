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
TestCase::TestCase(FpType format, Widen widen, float a, float b, float c) 
    : a_fp(a), b_fp(b), c_fp(c),
      is_fp32(format == FpType::FP32),
      is_fp16(format == FpType::FP16),
      is_bf16(format == FpType::BF16),
      is_widen(widen == Widen::Yes)
{
    memcpy(&a_bits, &a_fp, sizeof(uint32_t));
    memcpy(&b_bits, &b_fp, sizeof(uint32_t));
    memcpy(&c_bits, &c_fp, sizeof(uint32_t));

    float expected_fp = a_fp * b_fp + c_fp;
    memcpy(&expected_res_bits, &expected_fp, sizeof(uint32_t));
}

void TestCase::print_details() const {
    printf("--- Test Case ---\n");
    printf("Inputs: a=%.4f, b=%.4f, c=%.4f\n", a_fp, b_fp, c_fp);
    float expected_fp;
    memcpy(&expected_fp, &expected_res_bits, sizeof(float));
    printf("Expected: %.4f (HEX: 0x%x)\n", expected_fp, expected_res_bits);
}

bool TestCase::check_result(uint32_t dut_res) const {
    printf("--- Verification ---\n");
    float dut_res_fp;
    memcpy(&dut_res_fp, &dut_res, sizeof(float));
    printf("DUT Result: %.4f (HEX: 0x%x)\n", dut_res_fp, dut_res);

    if (dut_res == expected_res_bits) {
        printf(">>> PASSED <<<\n\n");
        return true;
    } else {
        printf(">>> FAILED <<<\n\n");
        return false;
    }
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
    top_->io_valid_in    = 1;
    top_->io_is_fp32 = test.is_fp32;
    top_->io_is_fp16 = test.is_fp16;
    top_->io_is_bf16 = test.is_bf16;
    top_->io_is_widen = test.is_widen;
    top_->io_a_in = test.a_bits;
    top_->io_b_in = test.b_bits;
    top_->io_c_in = test.c_bits;
    
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
    
    uint32_t dut_res = top_->io_res_out;
    test.check_result(dut_res);
}