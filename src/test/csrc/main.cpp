#include "sim.h"
#include <vector>
#include <cstdio>
#include <cstdlib> // For rand() and srand()
#include <ctime>   // For time()
#include <cstdint> // For uint32_t
#include <cmath>   // For std::isnan()

// 生成指定指数范围的随机浮点数
// exp_min: 最小指数（无偏置）
// exp_max: 最大指数（无偏置）
// 返回: 随机生成的32位浮点数
float gen_random_fp32(int exp_min, int exp_max) {
    union {
        float f;
        uint32_t i;
    } val;
    
    // 随机生成符号位 (1位)
    uint32_t sign = (rand() & 1) << 31;
    
    // 随机生成指数，范围[exp_min, exp_max]
    // IEEE 754偏置为127
    int exp_unbiased = exp_min + (rand() % (exp_max - exp_min + 1));
    uint32_t exp_biased = (exp_unbiased + 127) & 0xFF; // 加偏置并限制在8位
    uint32_t exp = exp_biased << 23;
    
    // 随机生成23位尾数，拆分多部分以确保充分随机性
    // 23位拆分为: 8位 + 8位 + 7位
    uint32_t mantissa_part1 = (rand() & 0xFF) << 15;        // 高8位 (位22-15)
    uint32_t mantissa_part2 = (rand() & 0xFF) << 7;         // 中8位 (位14-7)
    uint32_t mantissa_part3 = (rand() & 0x7F);              // 低7位 (位6-0)
    uint32_t mantissa = mantissa_part1 | mantissa_part2 | mantissa_part3;
    
    // 组合成完整的32位浮点数
    val.i = sign | exp | mantissa;
    
    return val.f;
}

int main(int argc, char *argv[]) {
  // 1. 初始化仿真器
  Simulator sim(argc, argv);
  srand(time(NULL)); // Seed random number generator for this run

  // 2. 创建一个测试用例的集合
  std::vector<TestCase> tests;
  
  // -- FP32 单精度浮点数测试 --
  tests.push_back(TestCase(FMA_Operands{-5.0f, -7.0f, -4789.0f}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands{1.0f, 2.0f, 0.0f}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands{2.5f, 10.0f, -30.0f}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands{0.0f, 123.45f, 67.89f}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands{-123.45, 0.0f, 67.89f}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands{123.0f, -67.0f, 0.0f}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands{5.0f, 123.0f, 67.0f}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands_Hex{0x40A00000, 0x40E00000, 0xC0000000}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands_Hex{0xbf7f7861, 0x7bede2c6, 0x7bdda74b}, ErrorType::ULP));
  tests.push_back(TestCase(FMA_Operands_Hex{0x58800c00, 0x58800400, 0xf1801000}, ErrorType::RelativeError));

  printf("\n---- Random tests for FP32 ----\n");
  // ---- FP32 任意值随机测试 ----
  int num_random_tests = 400;
  auto gen_any_float = []() -> float {
      union {
          float f;
          uint32_t i;
      } val;
      // rand() 通常最多返回 16-bit, 合并两个以获得完整的32-bit随机数
      do {
          val.i = ((uint32_t)rand() << 16) | (uint32_t)rand();
      } while (std::isnan(val.f));
      return val.f;
  };

  for (int i = 0; i < num_random_tests; ++i) {
      FMA_Operands ops = {gen_any_float(), gen_any_float(), gen_any_float()};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  //---------------------------------------------------------------

  // ---- 进行不同指数范围的测试 ----
  // 小数范围测试：指数[-50, -10]
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-50, -10), gen_random_fp32(-50, -10), gen_random_fp32(-50, -10)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  // 中等数值范围测试：指数[-10, 10]
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-10, 10), gen_random_fp32(-10, 10), gen_random_fp32(-10, 10)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  // 大数范围测试：指数[10, 50]
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(10, 50), gen_random_fp32(10, 50), gen_random_fp32(10, 50)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  // 更多测试
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-126, 20), gen_random_fp32(-126, 20), gen_random_fp32(-126, 20)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-126, 20), gen_random_fp32(-127, -126), gen_random_fp32(-126, 20)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-127, -126), gen_random_fp32(-126, 20), gen_random_fp32(-126, 20)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-126, 20), gen_random_fp32(-126, 20), gen_random_fp32(-127, -126)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-126, 20), gen_random_fp32(-127, -126), gen_random_fp32(-127, -126)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-127, -126), gen_random_fp32(-127, -126), gen_random_fp32(-127, -126)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < 400; ++i) {
      FMA_Operands ops = {gen_random_fp32(-127, 10), gen_random_fp32(-127, 10), gen_random_fp32(-127, 10)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }

  // // -- FP16 并行双路半精度浮点数测试 --
  // tests.push_back(TestCase({2.0f, 3.0f, 4.0f}, {5.0f, -1.0f, -8.0f}));
  // tests.push_back(TestCase({2.0f, 3.0f, -4.0f}, {5.0f, -7.0f, 8.0f}));
  // tests.push_back(TestCase({-12.34f, 5.8f, 683.6f}, {-6.7f, 789.4f, 98.7f}));


  // 3. 循环执行所有测试，遇到错误立即停止
  sim.reset(2);
  for (size_t i = 0; i < tests.size(); ++i) {
    printf("===== Running Test Case #%zu =====\n", i + 1);
    if (!sim.run_test(tests[i])) {
      printf("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
      printf("Test #%zu failed. Aborting execution.\n", i + 1);
      printf("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
      return 1; // 遇到错误，非零退出
    }
  }

  // 4. 如果执行到这里，说明所有测试都通过了
  printf("\n======================================\n");
  printf("All %zu tests passed! Great success!\n", tests.size());
  printf("======================================\n");

  return 0; // 所有测试通过，正常退出
}