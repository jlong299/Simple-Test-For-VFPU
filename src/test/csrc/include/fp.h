#ifndef __FP_H__
#define __FP_H__

#include "sim.h"
#include <math.h>
#include <stdio.h>

// FP16（半精度浮点数）格式：1位符号，5位指数，10位尾数
typedef uint16_t fp16_t;
// 将FP16转换为FP32（float）
float fp16_to_fp32(fp16_t h) {
    uint32_t sign = (h >> 15) & 0x01;
    uint32_t exp = (h >> 10) & 0x1f;
    uint32_t mant = h & 0x3ff;
    
    uint32_t f;
    
    if (exp == 0) {
        // 非规格化数或零
        if (mant == 0) {
            // 零
            f = sign << 31;
        } else {
            // 非规格化数：指数为1-15，尾数隐含0
            while (!(mant & 0x400)) {
                mant <<= 1;
                exp--;
            }
            exp++;
            mant &= 0x3ff;
            exp = 127 - 15 + exp;
            mant <<= 13;
            f = (sign << 31) | (exp << 23) | mant;
        }
    } else if (exp == 0x1f) {
        // 无穷大或NaN
        if (mant == 0) {
            // 无穷大
            f = (sign << 31) | (0xff << 23);
        } else {
            // NaN
            f = (sign << 31) | (0xff << 23) | (mant << 13);
        }
    } else {
        // 规格化数
        exp = 127 - 15 + exp;
        mant <<= 13;
        f = (sign << 31) | (exp << 23) | mant;
    }
    
    return *(float*)&f;
}


// 将FP32（float）转换为FP16
uint16_t fp32_to_fp16(float fp32) {
    // 将float转换为位表示
    uint32_t fp32_bits;
    memcpy(&fp32_bits, &fp32, sizeof(fp32_bits));
    
    // 提取FP32的各个字段
    uint32_t sign = (fp32_bits >> 31) & 0x1;        // 符号位
    uint32_t exp = (fp32_bits >> 23) & 0xFF;        // 8位指数
    uint32_t frac = fp32_bits & 0x7FFFFF;           // 23位尾数
    
    uint16_t fp16_bits;
    
    if (exp == 0) {
        // 处理零和非规格化数
        if (frac == 0) {
            // 零值
            fp16_bits = (sign << 15);
        } else {
            // FP32的非规格化数转换为FP16的零（通常太小了）
            fp16_bits = (sign << 15);
        }
    } else if (exp == 255) {
        // 处理无穷大和NaN
        if (frac == 0) {
            // 无穷大
            fp16_bits = (sign << 15) | 0x7C00;  // 指数全1，尾数为0
        } else {
            // NaN：保持符号位，指数全1，尾数非零
            uint16_t fp16_frac = (frac >> (23 - 10)) & 0x3FF;
            if (fp16_frac == 0) fp16_frac = 1;  // 确保NaN的尾数非零
            fp16_bits = (sign << 15) | 0x7C00 | fp16_frac;
        }
    } else {
        // 正常数转换
        // 指数转换：FP32指数 - FP32偏置(127) + FP16偏置(15) = exp - 112
        int32_t fp16_exp = (int32_t)exp - 112;
        
        // 检查指数是否溢出或下溢
        if (fp16_exp >= 31) {
            // 上溢：转换为无穷大
            fp16_bits = (sign << 15) | 0x7C00;
        } else if (fp16_exp <= 0) {
            // 下溢：尝试转换为非规格化数
            if (fp16_exp < -10) {
                // 太小，转换为零
                fp16_bits = (sign << 15);
            } else {
                // 转换为非规格化数
                // 需要将隐含的1位加入尾数，然后右移
                uint32_t full_frac = 0x800000 | frac;  // 加入隐含的1位
                int shift = 1 - fp16_exp;               // 需要右移的位数
                
                // 执行右移，包含舍入
                uint32_t shifted_frac = full_frac >> (shift + (23 - 10));
                
                // 简单的舍入到最近偶数
                if (full_frac & (1 << (shift + (23 - 10) - 1))) {
                    shifted_frac++;
                }
                
                // 检查舍入后是否溢出
                if (shifted_frac > 0x3FF) {
                    // 舍入导致溢出，变成最小的规格化数
                    fp16_bits = (sign << 15) | (1 << 10);
                } else {
                    fp16_bits = (sign << 15) | (shifted_frac & 0x3FF);
                }
            }
        } else {
            // 正常的规格化数转换
            // 尾数转换：从23位缩减到10位，包含舍入
            uint32_t fp16_frac = frac >> (23 - 10);  // 截取高10位
            
            // 简单的舍入到最近偶数
            if (frac & (1 << (23 - 10 - 1))) {  // 检查被截掉的最高位
                fp16_frac++;
                
                // 检查舍入后是否溢出
                if (fp16_frac > 0x3FF) {
                    fp16_frac = 0;
                    fp16_exp++;
                    
                    // 检查指数是否因舍入而溢出
                    if (fp16_exp >= 31) {
                        fp16_bits = (sign << 15) | 0x7C00;  // 无穷大
                    } else {
                        fp16_bits = (sign << 15) | (fp16_exp << 10) | fp16_frac;
                    }
                } else {
                    fp16_bits = (sign << 15) | (fp16_exp << 10) | fp16_frac;
                }
            } else {
                fp16_bits = (sign << 15) | (fp16_exp << 10) | fp16_frac;
            }
        }
    }
    
    return fp16_bits;
}


// BF16（半精度浮点数）格式：1位符号，8位指数，7位尾数
typedef uint16_t bf16_t;
// 将BF16转换为FP32（float）
float bf16_to_fp32(bf16_t h) {
    // BF16到FP32的转换非常简单：
    // BF16格式: 1位符号 + 8位指数 + 7位尾数
    // FP32格式: 1位符号 + 8位指数 + 23位尾数
    // 只需要将BF16左移16位，在尾数低位补0即可
    uint32_t f = ((uint32_t)h) << 16;
    return *(float*)&f;
}

// 将FP32（float）转换为BF16
uint16_t fp32_to_bf16(float fp32) {
    // 将float转换为位表示
    uint32_t fp32_bits;
    memcpy(&fp32_bits, &fp32, sizeof(fp32_bits));
    
    // FP32到BF16的转换：
    // 1. 取FP32的高16位（已经是BF16格式）
    // 2. 根据低16位进行RNE舍入
    uint16_t high16 = (uint16_t)(fp32_bits >> 16);
    uint16_t low16 = (uint16_t)(fp32_bits & 0xFFFF);
    
    // RNE舍入逻辑：需要检查guard, round, sticky位
    uint32_t guard_bit = (low16 >> 15) & 1;        // 低16位的最高位
    uint32_t round_bit = (low16 >> 14) & 1;        // 低16位的第二高位
    uint32_t sticky_bit = (low16 & 0x3FFF) ? 1 : 0; // 低16位的其余14位
    
    // RNE舍入：当guard位为1时
    if (guard_bit) {
        // 如果round或sticky位有任何一个为1，或者结果为奇数，则向上舍入
        if (round_bit || sticky_bit || (high16 & 1)) {
            high16++;
        }
    }
    
    return high16;
}

#endif