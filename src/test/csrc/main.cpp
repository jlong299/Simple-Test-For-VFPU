#include "sim.h"
#include <vector>
#include <cstdio>
#include <cstdlib> // For rand() and srand()
#include <ctime>   // For time()
#include <cstdint> // For uint32_t
#include <cmath>   // For std::isnan()

int main(int argc, char *argv[]) {
  // 1. 初始化仿真器
  Simulator sim(argc, argv);
  srand(time(NULL)); // Seed random number generator for this run

  // 2. 创建一个测试用例的集合
  std::vector<TestCase> tests;
  
  // -- FP32 单精度浮点数测试 --
  // tests.push_back(TestCase({-5.0f, -7.0f, -4789.0f}));
  // tests.push_back(TestCase({1.0f, 1.0f, 0.0f}));
  // tests.push_back(TestCase({2.5f, 10.0f, -30.0f}));
  // tests.push_back(TestCase({0.0f, 123.45f, 67.89f}));
  // tests.push_back(TestCase({7955446845526908612378321670809583616.0f, -31579445546259602921911555944653783040.0f, 527182002651136.0f}));
  // tests.push_back(TestCase(FMA_Operands_Hex{0xbf7f7861, 0x7bede2c6, 0x7bdda74b}));
  tests.push_back(TestCase(FMA_Operands_Hex{0x58800c00, 0x58800400, 0xf1801000}));


  // // -- FP32 任意值随机测试 --
  // int num_random_tests = 10000;
  // printf("\n--- Adding %d random full-range value tests for FP32 ---\n", num_random_tests);
  // auto gen_any_float = []() -> float {
  //     union {
  //         float f;
  //         uint32_t i;
  //     } val;
  //     // rand() 通常最多返回 16-bit, 合并两个以获得完整的32-bit随机数
  //     do {
  //         val.i = ((uint32_t)rand() << 16) | (uint32_t)rand();
  //     } while (std::isnan(val.f));
  //     return val.f;
  // };

  // for (int i = 0; i < num_random_tests; ++i) {
  //     FMA_Operands ops = {gen_any_float(), gen_any_float(), gen_any_float()};
  //     tests.push_back(TestCase(ops));
  // }
  // printf("-----------------------------------------------------------\n\n");

  // // -- FP32 极小值/非规格化值随机测试 --
  // int num_subnormal_tests = 1;
  // printf("\n--- Adding %d random subnormal value tests for FP32 ---\n", num_subnormal_tests);
  // auto gen_small_float = []() {
  //     // Generate a random float between -1.0 and 1.0, then scale it down
  //     float base = (rand() / (float)RAND_MAX * 2.0f) - 1.0f; 
  //     return base * 1e-40f; // Scale to a very small (subnormal) magnitude
  // };

  // for (int i = 0; i < num_subnormal_tests; ++i) {
  //     FMA_Operands ops = {gen_small_float(), gen_small_float(), gen_small_float()};
  //     tests.push_back(TestCase(ops));
  // }
  // printf("--------------------------------------------------------\n\n");

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