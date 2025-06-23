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
// 返回: 随机生成的32位浮点数的位表示
uint32_t gen_random_fp32(int exp_min, int exp_max) {
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
    uint32_t result = sign | exp | mantissa;
    
    return result;
}

// 生成指定指数范围的随机半精度浮点数
// exp_min: 最小指数（无偏置）
// exp_max: 最大指数（无偏置）
// 返回: 随机生成的16位浮点数（FP16格式）
uint16_t gen_random_fp16(int exp_min, int exp_max) {
    // 随机生成符号位 (1位)
    uint16_t sign = (rand() & 1) << 15;
    
    // 随机生成指数，范围[exp_min, exp_max]
    // IEEE 754 FP16偏置为15
    int exp_unbiased = exp_min + (rand() % (exp_max - exp_min + 1));
    uint16_t exp_biased = (exp_unbiased + 15) & 0x1F; // 加偏置并限制在5位
    uint16_t exp = exp_biased << 10;
    
    // 随机生成10位尾数，拆分多部分以确保充分随机性
    // 10位拆分为: 5位 + 5位
    uint16_t mantissa_part1 = (rand() & 0x1F) << 5;     // 高5位 (位9-5)
    uint16_t mantissa_part2 = (rand() & 0x1F);          // 低5位 (位4-0)
    uint16_t mantissa = mantissa_part1 | mantissa_part2;
    
    // 组合成完整的16位浮点数
    uint16_t fp16_bits = sign | exp | mantissa;
    
    return fp16_bits;
}

// 生成指定指数范围的随机BF16浮点数
// exp_min: 最小指数（无偏置）
// exp_max: 最大指数（无偏置）
// 返回: 随机生成的16位浮点数（BF16格式）
uint16_t gen_random_bf16(int exp_min, int exp_max) {
    // 随机生成符号位 (1位)
    uint16_t sign = (rand() & 1) << 15;
    
    // 随机生成指数，范围[exp_min, exp_max]
    // IEEE 754 BF16偏置为127（与FP32相同）
    int exp_unbiased = exp_min + (rand() % (exp_max - exp_min + 1));
    uint16_t exp_biased = (exp_unbiased + 127) & 0xFF; // 加偏置并限制在8位
    uint16_t exp = exp_biased << 7;
    
    // 随机生成7位尾数，拆分多部分以确保充分随机性
    // 7位拆分为: 4位 + 3位
    uint16_t mantissa_part1 = (rand() & 0xF) << 3;      // 高4位 (位6-3)
    uint16_t mantissa_part2 = (rand() & 0x7);           // 低3位 (位2-0)
    uint16_t mantissa = mantissa_part1 | mantissa_part2;
    
    // 组合成完整的16位浮点数
    uint16_t bf16_bits = sign | exp | mantissa;
    
    return bf16_bits;
}

// 生成任意随机的32位浮点数 (排除NaN)
uint32_t gen_any_fp32() {
    union {
        float f;
        uint32_t i;
    } val;
    // rand() 通常最多返回 16-bit, 合并两个以获得完整的32-bit随机数
    do {
        val.i = ((uint32_t)rand() << 16) | (uint32_t)rand();
    } while (std::isnan(val.f));
    return val.i;
}

// 生成任意随机的16位浮点数 (排除NaN)
uint16_t gen_any_fp16() {
    uint16_t val;
    // 生成完全随机的16位数值
    do {
        val = (uint16_t)rand();
    } while ((val & 0x7C00) == 0x7C00 && (val & 0x03FF) != 0); // 避免NaN值
    return val;
}

// 生成任意随机的BF16浮点数 (排除NaN)
uint16_t gen_any_bf16() {
    uint16_t val;
    // 生成完全随机的16位数值
    do {
        val = (uint16_t)rand();
    } while ((val & 0x7F80) == 0x7F80 && (val & 0x007F) != 0); // 避免NaN值
    return val;
}

int main(int argc, char *argv[]) {
  // 1. 初始化仿真器
  Simulator sim(argc, argv);
  srand(time(NULL)); // Seed random number generator for this run

  // 2. 创建一个测试用例的集合
  std::vector<TestCase> tests;
  
  bool test_fp32 = false;
  bool test_fp16 = true;
  bool test_bf16 = false;
  bool test_fp16_widen = false;
  bool test_bf16_widen = false;
  
  if (test_fp32) {
  // -- FP32 单精度浮点数测试 --
  tests.push_back(TestCase(FMA_Operands_Hex{0xC0A00000, 0xC0E00000, 0xC595C000}, ErrorType::Precise)); // -5.0f, -7.0f, -4789.0f
  tests.push_back(TestCase(FMA_Operands_Hex{0x3F800000, 0x40000000, 0x00000000}, ErrorType::Precise)); // 1.0f, 2.0f, 0.0f
  tests.push_back(TestCase(FMA_Operands_Hex{0x40200000, 0x41200000, 0xC1F00000}, ErrorType::Precise)); // 2.5f, 10.0f, -30.0f
  tests.push_back(TestCase(FMA_Operands_Hex{0x00000000, 0x42F6E666, 0x4287A3D7}, ErrorType::Precise)); // 0.0f, 123.45f, 67.89f
  tests.push_back(TestCase(FMA_Operands_Hex{0xC2F6E666, 0x00000000, 0x4287A3D7}, ErrorType::Precise)); // -123.45f, 0.0f, 67.89f
  tests.push_back(TestCase(FMA_Operands_Hex{0x42F60000, 0xC2860000, 0x00000000}, ErrorType::Precise)); // 123.0f, -67.0f, 0.0f
  tests.push_back(TestCase(FMA_Operands_Hex{0x40A00000, 0x42F60000, 0x42860000}, ErrorType::Precise)); // 5.0f, 123.0f, 67.0f
  tests.push_back(TestCase(FMA_Operands_Hex{0x40A00000, 0x40E00000, 0xC0000000}, ErrorType::Precise));
  tests.push_back(TestCase(FMA_Operands_Hex{0xbf7f7861, 0x7bede2c6, 0x7bdda74b}, ErrorType::ULP));
  tests.push_back(TestCase(FMA_Operands_Hex{0x58800c00, 0x58800400, 0xf1801000}, ErrorType::RelativeError));
  tests.push_back(TestCase(FMA_Operands_Hex{0x816849E7, 0x00B6D8A2, 0x08F0CF76}, ErrorType::ULP));

  printf("\n---- Random tests for FP32 ----\n");
  int num_random_tests_32 = 200;
  // ---- FP32 任意值随机测试 ----
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_any_fp32(), gen_any_fp32(), gen_any_fp32()};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  // ---- 进行不同指数范围的测试 ----
  // 小数范围测试：指数[-50, -10]
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_random_fp32(-50, -10), gen_random_fp32(-50, -10), gen_random_fp32(-50, -10)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  // 中等数值范围测试：指数[-10, 10]
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_random_fp32(-10, 10), gen_random_fp32(-10, 10), gen_random_fp32(-10, 10)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  // 大数范围测试：指数[10, 50]
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_random_fp32(10, 50), gen_random_fp32(10, 50), gen_random_fp32(10, 50)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  // 更多测试
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_random_fp32(-126, 20), gen_random_fp32(-126, 20), gen_random_fp32(-126, 20)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_random_fp32(-126, 20), gen_random_fp32(-127, -126), gen_random_fp32(-126, 20)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_random_fp32(-127, -126), gen_random_fp32(-126, 20), gen_random_fp32(-126, 20)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
  for (int i = 0; i < num_random_tests_32; ++i) {
      FMA_Operands_Hex ops = {gen_random_fp32(-126, 20), gen_random_fp32(-126, 20), gen_random_fp32(-127, -126)};
      tests.push_back(TestCase(ops, ErrorType::RelativeError));
  }
      for (int i = 0; i < num_random_tests_32; ++i) {
        FMA_Operands_Hex ops = {gen_random_fp32(-126, 20), gen_random_fp32(-127, -126), gen_random_fp32(-127, -126)};
        tests.push_back(TestCase(ops, ErrorType::RelativeError));
    }
    for (int i = 0; i < num_random_tests_32; ++i) {
        FMA_Operands_Hex ops = {gen_random_fp32(-127, -126), gen_random_fp32(-127, -126), gen_random_fp32(-127, -126)};
        tests.push_back(TestCase(ops, ErrorType::RelativeError));
    }
    for (int i = 0; i < num_random_tests_32; ++i) {
        FMA_Operands_Hex ops = {gen_random_fp32(-127, 10), gen_random_fp32(-127, 10), gen_random_fp32(-127, 10)};
        tests.push_back(TestCase(ops, ErrorType::RelativeError));
    }
  }


  if (test_fp16) {
  // -- FP16 并行双路半精度浮点数测试 --
  // TODO: 验证c代码是将fp16转换成fp32之后做a*b+c，最后将结果转为fp16。
  //       这样，在a*b接近fp16的inf时，情况会比较复杂，会有多种情况导致类似ref-model和dut的a*b结果一方是inf，另一方是接近inf的数。
  //       这样在加上c，会导致最后结果出现较大差异但又不好判断误差是否合理（比如说一个结果是inf，另一个不是）。
  //       因此，本测试可能在上述情况下出现报错，需甄别！！！！  暂时不修这个问题。
  // 基本运算测试
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0x4000, 0x4000}, FMA_Operands_Hex_16{0x4200, 0x3c00, 0x4200}, ErrorType::Precise)); // 1.0 * 2.0 + 2.0 | 3.0 * 1.0 + 3.0
  tests.push_back(TestCase(FMA_Operands_Hex_16{0xbc00, 0x4000, 0x4000}, FMA_Operands_Hex_16{0x3c00, 0xc000, 0x3c00}, ErrorType::Precise)); // -1.0 * 2.0 + 2.0 | 1.0 * -2.0 + 1.0
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0xbc00, 0x4000}, FMA_Operands_Hex_16{0x4400, 0x3800, 0x3c00}, ErrorType::Precise)); // 1.0 * -2.0 + 2.0 | 4.0 * 0.5 + 1.0
  // 零值测试
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x0000, 0x4000, 0x4000}, FMA_Operands_Hex_16{0x4000, 0x0000, 0x4200}, ErrorType::Precise)); // 0.0 * 2.0 + 2.0 | 2.0 * 0.0 + 3.0
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0x0000, 0x4000}, FMA_Operands_Hex_16{0x0000, 0x4200, 0x3800}, ErrorType::Precise)); // 1.0 * 0.0 + 2.0 | 0.0 * 3.0 + 0.5
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0x4000, 0x0000}, FMA_Operands_Hex_16{0x4200, 0x3800, 0x0000}, ErrorType::Precise)); // 1.0 * 2.0 + 0.0 | 3.0 * 0.5 + 0.0
  // 无穷大测试
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x7c00, 0x4000, 0x4000}, FMA_Operands_Hex_16{0x3c00, 0xfc00, 0x4000}, ErrorType::Precise)); // +inf * 2.0 + 2.0 | 1.0 * -inf + 2.0
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0x7c00, 0x4000}, FMA_Operands_Hex_16{0x7c00, 0x7c00, 0x4200}, ErrorType::Precise)); // 1.0 * +inf + 2.0 | +inf * inf + 3.0
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0x4000, 0x7c00}, FMA_Operands_Hex_16{0x4000, 0x3c00, 0xfc00}, ErrorType::Precise)); // 1.0 * 2.0 + +inf | 2.0 * 1.0 + -inf
  // 非规格化数测试
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x0001, 0x4000, 0x4000}, FMA_Operands_Hex_16{0x03ff, 0x3c00, 0x0200}, ErrorType::Precise)); // 最小非规格化数 * 2.0 + 2.0 | 最大非规格化数 * 1.0 + 其他非规格化数
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0x0001, 0x4000}, FMA_Operands_Hex_16{0x4000, 0x03ff, 0x3c00}, ErrorType::Precise)); // 1.0 * 最小非规格化数 + 2.0 | 2.0 * 最大非规格化数 + 1.0
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x3c00, 0x4000, 0x0001}, FMA_Operands_Hex_16{0x0200, 0x4200, 0x03ff}, ErrorType::Precise)); // 1.0 * 2.0 + 最小非规格化数 | 非规格化数 * 3.0 + 最大非规格化数
  // 其他
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x4d6f, 0x1ea8, 0x9ab1}, FMA_Operands_Hex_16{0x5455, 0xe39c, 0x7526}, ErrorType::ULP));
  tests.push_back(TestCase(FMA_Operands_Hex_16{0x668, 0x5b00, 0xa59b}, FMA_Operands_Hex_16{0x8f63, 0x575, 0xb918}, ErrorType::RelativeError));
  // tests.push_back(TestCase(FMA_Operands_Hex_16{0xe42b, 0xdbaa, 0xdeb6}, FMA_Operands_Hex_16{0x5be8, 0xdc0c, 0x6aa3}, ErrorType::RelativeError));

  printf("\n---- Random tests for FP16 ----\n");
  int num_random_tests_16 = 200;
  // ---- FP16 任意值随机测试 ----
  for (int i = 0; i < num_random_tests_16; ++i) {
      // 生成两组FP16随机操作数
      FMA_Operands_Hex_16 ops1 = {gen_any_fp16(), gen_any_fp16(), gen_any_fp16()};
      FMA_Operands_Hex_16 ops2 = {gen_any_fp16(), gen_any_fp16(), gen_any_fp16()};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  // ---- 进行不同指数范围的FP16随机测试 ----
  // 小数范围测试：指数[-15, -5]
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(-15, -5), gen_random_fp16(-15, -5), gen_random_fp16(-15, -5)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(-15, -5), gen_random_fp16(-15, -5), gen_random_fp16(-15, -5)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  // 中等数值范围测试：指数[-5, 5]
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(-5, 5), gen_random_fp16(-5, 5), gen_random_fp16(-5, 5)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(-5, 5), gen_random_fp16(-5, 5), gen_random_fp16(-5, 5)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  // 大数范围测试：指数[5, 15]
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(5, 15), gen_random_fp16(5, 15), gen_random_fp16(5, 15)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(5, 15), gen_random_fp16(5, 15), gen_random_fp16(5, 15)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  // 更多测试
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, 15), gen_random_fp16(-15, 15)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, 15), gen_random_fp16(-15, 15)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, 15), gen_random_fp16(-15, 15)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, -14), gen_random_fp16(-15, 15)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, 15), gen_random_fp16(-15, -14)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(-15, 15), gen_random_fp16(-15, -14), gen_random_fp16(-15, -14)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, -14), gen_random_fp16(-15, 15)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, -14), gen_random_fp16(-15, -14)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  for (int i = 0; i < num_random_tests_16; ++i) {
      FMA_Operands_Hex_16 ops1 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, -14), gen_random_fp16(-15, -14)};
      FMA_Operands_Hex_16 ops2 = {gen_random_fp16(-15, -14), gen_random_fp16(-15, -14), gen_random_fp16(-15, -14)};
      tests.push_back(TestCase(ops1, ops2, ErrorType::ULP));
  }
  }

  if (test_bf16) {
    // -- BF16 并行双路半精度浮点数测试 --
    // TODO: 验证c代码是将bf16转换成fp32之后做a*b+c，最后将结果转为bf16。
    //       这样，在a*b接近bf16的inf时，情况会比较复杂，会有多种情况导致类似ref-model和dut的a*b结果一方是inf，另一方是接近inf的数。
    //       这样在加上c，会导致最后结果出现较大差异但又不好判断误差是否合理（比如说一个结果是inf，另一个不是）。
    //       因此，本测试可能在上述情况下出现报错，需甄别！！！！  暂时不修这个问题。
    // 基本运算测试
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0x4000, 0x4000}, FMA_Operands_Hex_BF16{0x4040, 0x3f80, 0x4040}, ErrorType::Precise)); // 1.0 * 2.0 + 2.0 | 3.0 * 1.0 + 3.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0xbf80, 0x4000, 0x4000}, FMA_Operands_Hex_BF16{0x3f80, 0xc000, 0x3f80}, ErrorType::Precise)); // -1.0 * 2.0 + 2.0 | 1.0 * -2.0 + 1.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0xbf80, 0x4000}, FMA_Operands_Hex_BF16{0x4080, 0x3f00, 0x3f80}, ErrorType::Precise)); // 1.0 * -1.0 + 2.0 | 4.0 * 0.5 + 1.0
    // 零值测试
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x0000, 0x4000, 0x4000}, FMA_Operands_Hex_BF16{0x4000, 0x0000, 0x4040}, ErrorType::Precise)); // 0.0 * 2.0 + 2.0 | 2.0 * 0.0 + 3.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0x0000, 0x4000}, FMA_Operands_Hex_BF16{0x0000, 0x4040, 0x3f00}, ErrorType::Precise)); // 1.0 * 0.0 + 2.0 | 0.0 * 3.0 + 0.5
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0x4000, 0x0000}, FMA_Operands_Hex_BF16{0x4040, 0x3f00, 0x0000}, ErrorType::Precise)); // 1.0 * 2.0 + 0.0 | 3.0 * 0.5 + 0.0
    // 无穷大测试
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x7f80, 0x4000, 0x4000}, FMA_Operands_Hex_BF16{0x3f80, 0xff80, 0x4000}, ErrorType::Precise)); // +inf * 2.0 + 2.0 | 1.0 * -inf + 2.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0x7f80, 0x4000}, FMA_Operands_Hex_BF16{0x7f80, 0x7f80, 0x4040}, ErrorType::Precise)); // 1.0 * +inf + 2.0 | +inf * inf + 3.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0x4000, 0x7f80}, FMA_Operands_Hex_BF16{0x4000, 0x3f80, 0xff80}, ErrorType::Precise)); // 1.0 * 2.0 + +inf | 2.0 * 1.0 + -inf
    // 非规格化数测试（BF16的非规格化数非常小）
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x0001, 0x4000, 0x4000}, FMA_Operands_Hex_BF16{0x007f, 0x3f80, 0x0020}, ErrorType::Precise)); // 最小非规格化数 * 2.0 + 2.0 | 最大非规格化数 * 1.0 + 其他非规格化数
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0x0001, 0x4000}, FMA_Operands_Hex_BF16{0x4000, 0x007f, 0x3f80}, ErrorType::Precise)); // 1.0 * 最小非规格化数 + 2.0 | 2.0 * 最大非规格化数 + 1.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x3f80, 0x4000, 0x0001}, FMA_Operands_Hex_BF16{0x0020, 0x4040, 0x007f}, ErrorType::Precise)); // 1.0 * 2.0 + 最小非规格化数 | 非规格化数 * 3.0 + 最大非规格化数
    // 边界值测试
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x7f7f, 0x3f80, 0x0000}, FMA_Operands_Hex_BF16{0x0080, 0x3f80, 0x0000}, ErrorType::ULP)); // 最大规格化数 * 1.0 + 0.0 | 最小规格化数 * 1.0 + 0.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0xff7f, 0x3f80, 0x0000}, FMA_Operands_Hex_BF16{0x8080, 0x3f80, 0x0000}, ErrorType::ULP)); // -最大规格化数 * 1.0 + 0.0 | -最小规格化数 * 1.0 + 0.0
    // 接近溢出的测试
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x7f00, 0x4000, 0x3f80}, FMA_Operands_Hex_BF16{0x7e80, 0x4040, 0x3f00}, ErrorType::ULP)); // 大数 * 2.0 + 1.0 | 大数 * 3.0 + 0.5
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x7f7f, 0x3f00, 0x7f7f}, FMA_Operands_Hex_BF16{0x7e80, 0x3f00, 0x7e80}, ErrorType::ULP)); // 最大数 * 0.5 + 最大数 | 大数 * 0.5 + 大数
    // 精度损失边界测试  
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x4000, 0x3e80, 0x3d80}, FMA_Operands_Hex_BF16{0x4040, 0x3e00, 0x3d00}, ErrorType::ULP)); // 2.0 * 小数 + 更小数 | 3.0 * 小数 + 更小数
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x5000, 0x3d80, 0x5000}, FMA_Operands_Hex_BF16{0x4c80, 0x3d00, 0x4c80}, ErrorType::ULP)); // 大数 * 极小数 + 大数 | 中数 * 极小数 + 中数
    // 符号组合的复杂测试
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0xc000, 0xc000, 0x4080}, FMA_Operands_Hex_BF16{0xc040, 0xc000, 0x4040}, ErrorType::ULP)); // (-2.0) * (-2.0) + 4.0 | (-3.0) * (-2.0) + 3.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x4000, 0xc000, 0x4000}, FMA_Operands_Hex_BF16{0x4040, 0xbf80, 0x4080}, ErrorType::ULP)); // 2.0 * (-2.0) + 2.0 | 3.0 * (-1.0) + 4.0
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0xc000, 0x4000, 0xc000}, FMA_Operands_Hex_BF16{0xc040, 0x4000, 0xc080}, ErrorType::ULP)); // (-2.0) * 2.0 + (-2.0) | (-3.0) * 2.0 + (-4.0)
    // 其他复杂测试用例
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x42a5, 0x3e12, 0x4123}, FMA_Operands_Hex_BF16{0x4567, 0x3d89, 0x40ab}, ErrorType::ULP));
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x4012, 0x4234, 0x3fab}, FMA_Operands_Hex_BF16{0x4156, 0x3e78, 0x4009}, ErrorType::RelativeError));
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x9a1d, 0x1fa1, 0x8011}, FMA_Operands_Hex_BF16{0xa174, 0xcafa, 0x455d}, ErrorType::ULP));
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x80e1, 0x80ed, 0xc0}, FMA_Operands_Hex_BF16{0x80cd, 0x806d, 0x8000}, ErrorType::ULP_or_RelativeError));
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0x80e1, 0x80ed, 0x0000}, FMA_Operands_Hex_BF16{0x80cd, 0x806d, 0x0000}, ErrorType::ULP));
    tests.push_back(TestCase(FMA_Operands_Hex_BF16{0xbf80, 0x0200, 0x0200}, FMA_Operands_Hex_BF16{0xbf80, 0x0200, 0x0200}, ErrorType::ULP));
    
    printf("\n---- Random tests for BF16 ----\n");
    int num_random_tests_bf16 = 200;
    
    // ---- BF16 任意值随机测试 ----
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        // 生成两组BF16随机操作数
        FMA_Operands_Hex_BF16 ops1 = {gen_any_bf16(), gen_any_bf16(), gen_any_bf16()};
        FMA_Operands_Hex_BF16 ops2 = {gen_any_bf16(), gen_any_bf16(), gen_any_bf16()};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    
    // ---- 进行不同指数范围的BF16随机测试 ----
    // 小数范围测试：指数[-50, -10]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-50, -10), gen_random_bf16(-50, -10), gen_random_bf16(-50, -10)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-50, -10), gen_random_bf16(-50, -10), gen_random_bf16(-50, -10)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 中等数值范围测试：指数[-10, 10]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-10, 10), gen_random_bf16(-10, 10), gen_random_bf16(-10, 10)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-10, 10), gen_random_bf16(-10, 10), gen_random_bf16(-10, 10)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 大数范围测试：指数[10, 50]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(10, 50), gen_random_bf16(10, 50), gen_random_bf16(10, 50)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(10, 50), gen_random_bf16(10, 50), gen_random_bf16(10, 50)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 极端范围测试：指数[-126, 127]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, 127), gen_random_bf16(-126, 127), gen_random_bf16(-126, 127)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, 127), gen_random_bf16(-126, 127), gen_random_bf16(-126, 127)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 非规格化数边界测试：指数[-126, -125]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, -125), gen_random_bf16(-126, 20), gen_random_bf16(-126, 20)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, 20), gen_random_bf16(-126, -125), gen_random_bf16(-126, 20)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 混合精度范围测试
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, 20), gen_random_bf16(-126, 20), gen_random_bf16(-126, -125)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, 20), gen_random_bf16(-126, -125), gen_random_bf16(-126, -125)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 高精度范围测试
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-126, -125), gen_random_bf16(-126, -125), gen_random_bf16(-126, 20)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-126, -125), gen_random_bf16(-126, -125), gen_random_bf16(-126, -125)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 全范围混合测试
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-127, 10), gen_random_bf16(-127, 10), gen_random_bf16(-127, 10)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-127, 10), gen_random_bf16(-127, 10), gen_random_bf16(-127, 10)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 相对误差测试（较高精度要求）
    for (int i = 0; i < num_random_tests_bf16 / 5; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-20, 20), gen_random_bf16(-20, 20), gen_random_bf16(-20, 20)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-20, 20), gen_random_bf16(-20, 20), gen_random_bf16(-20, 20)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
    // 极端范围测试：指数[-127, -126]
    for (int i = 0; i < num_random_tests_bf16; ++i) {
        FMA_Operands_Hex_BF16 ops1 = {gen_random_bf16(-127, -126), gen_random_bf16(-127, -126), gen_random_bf16(-127, -126)};
        FMA_Operands_Hex_BF16 ops2 = {gen_random_bf16(-127, -126), gen_random_bf16(-127, -126), gen_random_bf16(-127, -126)};
        tests.push_back(TestCase(ops1, ops2, ErrorType::ULP_or_RelativeError));
    }
  }

  if (test_fp16_widen) {
  // -- FP16 widen 测试 --
  // FP16 widen 测试：混合精度 (a,b=FP16, c=FP32, result=FP32)
  // 基础测试用例
  tests.push_back(TestCase(FMA_Operands_FP16_Widen{0x3c00, 0x4000, 0x40000000}, ErrorType::Precise)); // 1.0 * 2.0 + 2.0 = 4.0
  tests.push_back(TestCase(FMA_Operands_FP16_Widen{0xbc00, 0x4000, 0x40000000}, ErrorType::Precise)); // -1.0 * 2.0 + 2.0 = 0.0
  tests.push_back(TestCase(FMA_Operands_FP16_Widen{0x3c00, 0xbc00, 0x40400000}, ErrorType::Precise)); // 1.0 * -1.0 + 3.0 = 2.0
  tests.push_back(TestCase(FMA_Operands_FP16_Widen{0x0000, 0x4000, 0x40000000}, ErrorType::Precise)); // 0.0 * 2.0 + 2.0 = 2.0
  
  printf("\n---- Random tests for FP16 Widen ----\n");
  int num_random_tests_fp16_widen = 200;
  // ---- FP16 widen 任意值随机测试 ----
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_any_fp16(), gen_any_fp16(), gen_any_fp32()};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 正常范围测试
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-10, 10), gen_random_fp16(-10, 10), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 小数范围测试 - FP16指数范围
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-15, -5), gen_random_fp16(-15, -5), gen_random_fp32(-50, -10)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 大数范围测试 - FP16指数范围  
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(5, 15), gen_random_fp16(5, 15), gen_random_fp32(10, 50)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 混合指数范围测试
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-15, 15), gen_random_fp16(-15, 15), gen_random_fp32(-126, 127)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 非规格化数边界测试 - FP16
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-15, -14), gen_random_fp16(-15, 15), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-15, 15), gen_random_fp16(-15, -14), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 极端范围测试 - 接近FP16溢出
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(14, 15), gen_random_fp16(14, 15), gen_random_fp32(50, 100)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 极端下溢测试 - 接近FP16下溢
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-15, -14), gen_random_fp16(-15, -14), gen_random_fp32(-100, -50)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 高精度FP32 c值测试
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-5, 5), gen_random_fp16(-5, 5), gen_random_fp32(-126, -100)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-5, 5), gen_random_fp16(-5, 5), gen_random_fp32(100, 126)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 全范围随机测试 - 最全面的测试
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-15, 15), gen_random_fp16(-15, 15), gen_random_fp32(-126, 127)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 特殊组合测试 - 一个操作数极大，另一个极小
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(10, 15), gen_random_fp16(-15, -10), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    for (int i = 0; i < num_random_tests_fp16_widen; ++i) {
        FMA_Operands_FP16_Widen ops = {gen_random_fp16(-15, -10), gen_random_fp16(10, 15), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
  }

  if (test_bf16_widen) {
  // -- BF16 widen 测试 --
  // BF16 widen 测试：混合精度 (a,b=BF16, c=FP32, result=FP32)
  // 基础测试用例
  tests.push_back(TestCase(FMA_Operands_BF16_Widen{0x3f80, 0x4000, 0x40000000}, ErrorType::Precise)); // 1.0 * 2.0 + 2.0 = 4.0
  tests.push_back(TestCase(FMA_Operands_BF16_Widen{0xbf80, 0x4000, 0x40000000}, ErrorType::Precise)); // -1.0 * 2.0 + 2.0 = 0.0
  tests.push_back(TestCase(FMA_Operands_BF16_Widen{0x3f80, 0xbf80, 0x40400000}, ErrorType::Precise)); // 1.0 * -1.0 + 3.0 = 2.0
  tests.push_back(TestCase(FMA_Operands_BF16_Widen{0x0000, 0x4000, 0x40000000}, ErrorType::Precise)); // 0.0 * 2.0 + 2.0 = 2.0

  printf("\n---- Random tests for BF16 Widen ----\n");
  int num_random_tests_bf16_widen = 200;
  // ---- BF16 widen 任意值随机测试 ----
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_any_bf16(), gen_any_bf16(), gen_any_fp32()};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 正常范围测试
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(-10, 10), gen_random_bf16(-10, 10), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 小数范围测试 - BF16指数范围
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(-50, -10), gen_random_bf16(-50, -10), gen_random_fp32(-50, -10)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 大数范围测试 - BF16指数范围  
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(10, 50), gen_random_bf16(10, 50), gen_random_fp32(10, 50)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 混合指数范围测试
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(-126, 127), gen_random_bf16(-126, 127), gen_random_fp32(-126, 127)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 非规格化数边界测试 - BF16
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(-126, -125), gen_random_bf16(-126, 20), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(-126, 20), gen_random_bf16(-126, -125), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 全范围随机测试 - 最全面的测试
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(-127, 127), gen_random_bf16(-127, 127), gen_random_fp32(-127, 127)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    // 特殊组合测试 - 一个操作数极大，另一个极小
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(50, 100), gen_random_bf16(-100, -50), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
    for (int i = 0; i < num_random_tests_bf16_widen; ++i) {
        FMA_Operands_BF16_Widen ops = {gen_random_bf16(-100, -50), gen_random_bf16(50, 100), gen_random_fp32(-20, 20)};
        tests.push_back(TestCase(ops, ErrorType::ULP));
    }
  }



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