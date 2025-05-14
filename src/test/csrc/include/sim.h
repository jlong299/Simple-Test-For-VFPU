#ifndef __SIM_H__
#define __SIM_H__

#include <stdint.h>

#define VLEN 512
#define XLEN 32
void single_cycle();
void reset(int n);
void sim_init(int argc, char *argv[]);
void sim_exit();
void sim_main(int argc, char *argv[]);


typedef struct {
    uint32_t vs1[VLEN/XLEN];
    uint32_t vs2[VLEN/XLEN];
    uint32_t vd;
    uint32_t expected_vd;
    uint32_t is_vfredsum;
    uint32_t is_vfredmax;
    uint32_t vlmul;
    uint32_t round_mode;
    uint32_t fp_format;
    uint32_t is_vec;
    uint32_t index;
} IOput;

#define VLMUL8 3
#define VLMUL4 2
#define VLMUL2 1
#define VLMUL1 0

#define FP32 2
#define FP16 1
#define BF16 0

#endif