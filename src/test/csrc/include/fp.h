#ifndef __FP_H__
#define __FP_H__

#include "sim.h"
#include <math.h>
#include <stdio.h>

typedef unsigned short half;

uint16_t fp32_to_fp16(float f) {
    uint32_t fp32;
    memcpy(&fp32, &f, sizeof(fp32));

    uint32_t sign = (fp32 >> 16) & 0x8000;
    int exp = ((fp32 >> 23) & 0xFF) - 112;   // 调整指数偏移
    uint32_t frac = (fp32 & 0x007FFFFF) >> 13;

    if (exp <= 0) {
        // Subnormal 数处理
        if (exp < -10) return sign;  // 太小，返回0
        frac = (frac | 0x0800) >> (1 - exp);
        return sign | frac;
    } else if (exp >= 31) {
        // 溢出处理
        return sign | 0x7C00;  // Inf
    }

    return sign | (exp << 10) | frac;
}


float half_to_float(half h) {
    unsigned short sign = (h >> 15) & 0x1;
    unsigned short exponent = (h >> 10) & 0x1F;
    unsigned short mantissa = h & 0x3FF;

    if (exponent == 0) {
        if (mantissa == 0) {
            return sign ? -0.0f : 0.0f;  // +0 or -0
        }
        else {
            return sign ? -pow(2, -14) * (mantissa / 1024.0f) : pow(2, -14) * (mantissa / 1024.0f);
        }
    }
    else if (exponent == 31)
    {
        if (mantissa == 0) {
            return sign ? -INFINITY : INFINITY;
        } else {
            return NAN;
        }    
    }
    
    //IEEE 754
    float result = (1.0f + mantissa / 1024.0f) * pow(2, exponent - 15);
    return sign ? -result : result;

}

inline float get_fp32_from_uint32(uint32_t data, bool high) {
    uint16_t fp16 = high ? (data >> 16) : (data & 0xFFFF);
    return half_to_float(fp16);
}

// For VLEN = 256
void fp16_print(uint32_t *src, uint32_t vlen){
    if (src == NULL) {
        printf("Error: Null pointer passed to fp16_print\n");
        return;
    }
    for (int j = vlen/32 -1; j >= 0; j--){
        for(int k = 32/16 - 1; k >= 0; k--){
            printf("%12.4f ", half_to_float((src[j] >> 16*k) & 0xffff));
        }
        if(j%4 == 0){
            printf("\n");
        }
    }
}

void uint64_print(uint64_t *src, uint64_t vlen, uint64_t xlen){
    if (src == NULL) {
        printf("Error: Null pointer passed to fp16_print\n");
        return;
    }
    for (int j = vlen/xlen -1; j >= 0; j--){
            printf("%ld ", src[j]);
    }
    printf("\n");
}

void int8_print(uint64_t *src, uint64_t vlen, uint64_t xlen){
    if (src == NULL) {
        printf("Error: Null pointer passed to fp16_print\n");
        return;
    }
    for (int j = vlen/xlen -1; j >= 0; j--){
        for(int k = xlen/8 - 1; k >= 0; k--){
            printf("%6d", (int8_t)((src[j] >> 8*k) & 0xff));
        }
        if(j%4 == 0){
            printf("\n");
        }
    }
}

void int16_print(uint64_t *src, uint64_t vlen, uint64_t xlen){
    if (src == NULL) {
        printf("Error: Null pointer passed to fp16_print\n");
        return;
    }
    for (int j = vlen/xlen -1; j >= 0; j--){
        for(int k = xlen/16 - 1; k >= 0; k--){
            printf("%10d", (int16_t)((src[j] >> 16*k) & 0xffff));
        }
        if(j%4 == 0){
            printf("\n");
        }
    }
}

uint64_t rand64() {
  uint64_t tmp = rand();
  tmp = (tmp << 32) + (uint32_t) rand();
  return tmp;
}

uint32_t rand32() {
  return (uint32_t) rand();
}

#endif