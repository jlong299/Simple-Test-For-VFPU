#include "sim.h"
#include <vector>

int main(int argc, char *argv[]) {
  // 1. 初始化仿真器
  Simulator sim(argc, argv);

  // 2. 创建一个测试用例的集合
  std::vector<TestCase> tests;
  tests.push_back(TestCase(FpType::FP32, Widen::No, -5.0f, -7.0f, -4789.0f));
  tests.push_back(TestCase(FpType::FP32, Widen::No, 1.0f, 1.0f, 0.0f));
  tests.push_back(TestCase(FpType::FP32, Widen::No, 2.5f, 10.0f, -30.0f));
  tests.push_back(TestCase(FpType::FP32, Widen::No, 0.0f, 123.45f, 67.89f));

  // 3. 循环执行所有测试
  sim.reset(2);
  for (const auto& test : tests) {
    sim.run_test(test);
  }

  return 0;
}