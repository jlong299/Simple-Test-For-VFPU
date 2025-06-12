#include "sim.h"
#include <vector>
// #include <tuple> // No longer needed

int main(int argc, char *argv[]) {
  // 1. 初始化仿真器
  Simulator sim(argc, argv);

  // 2. 创建一个测试用例的集合
  std::vector<TestCase> tests;
  
  // -- FP32 单精度浮点数测试 --
  tests.push_back(TestCase({-5.0f, -7.0f, -4789.0f}));
  tests.push_back(TestCase({1.0f, 1.0f, 0.0f}));
  tests.push_back(TestCase({2.5f, 10.0f, -30.0f}));
  tests.push_back(TestCase({0.0f, 123.45f, 67.89f}));

  // -- FP16 并行双路半精度浮点数测试 --
  // 使用 C++11 的列表初始化 (list initialization) 来创建 FMA_Operands 实例
  tests.push_back(TestCase(
    {2.0f, 3.0f, 4.0f},    // op1: 2*3+4 = 10
    {5.0f, -1.0f, -8.0f}   // op2: 5*(-1)+(-8) = -13
  ));

  // 3. 循环执行所有测试
  sim.reset(2);
  for (const auto& test : tests) {
    sim.run_test(test);
  }

  return 0;
}