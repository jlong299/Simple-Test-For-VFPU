// sim_c/sim.cc
#include <iostream>
#include <bitset>

#include <verilated.h>
#include "Vtop.h"
#ifdef VCD
    #include "verilated_vcd_c.h"
    VerilatedVcdC* tfp = nullptr;
#endif

#include "sim.h"
#include "fp.h"

using namespace std; 

// init pointers
const std::unique_ptr<VerilatedContext> contextp{new VerilatedContext};
const std::unique_ptr<Vtop> top{new Vtop{contextp.get(), "TOP"}};

IOput Sim_IO;

/* sim initial */
void sim_init(int argc, char *argv[]) {
    top->reset = 1;
    top->clock = 0;
#ifdef VCD
    contextp->traceEverOn(true);
    tfp = new VerilatedVcdC;
    top->trace(tfp, 99);
    tfp->open("build/fma/top.vcd");
#endif
    Verilated::commandArgs(argc,argv);
}

/* sim exit */
void sim_exit() {
    // finish work, delete pointer
    top->final();
#if VCD
    tfp->close();
    tfp = nullptr;
#endif
}

void fp32_print(uint32_t *src, uint64_t vlen) {
    if (src == NULL) {
        printf("Error: Null pointer passed to fp32_print\n");
        return;
    }

    float *fsrc = (float*)src;   // 强制转换成 float 指针

    // 将位宽转为浮点数数量
    uint64_t num_floats = vlen / 8 / sizeof(float);  

    for (uint64_t j = 0; j < num_floats; j++) {     // 正序遍历
        printf("%f ", fsrc[j]);
        if ((j + 1) % 4 == 0) {                     // 每行输出4个数
            printf("\n");
        }
    }
}

void single_cycle() {
    contextp->timeInc(1);
    top->clock = 0; top->eval();
#ifdef VCD
 tfp->dump(contextp->time());
#endif

    contextp->timeInc(1);
    top->clock = 1; top->eval();
#ifdef VCD
 tfp->dump(contextp->time());
#endif
}

void get_expected_result() {
    if (top->io_is_fp32) {
        float a = *reinterpret_cast<float*>(&Sim_IO.a_in);
        float b = *reinterpret_cast<float*>(&Sim_IO.b_in);
        float c = *reinterpret_cast<float*>(&Sim_IO.c_in);

        printf("Input values:\n");
        printf("a = %f\n", a);
        printf("b = %f\n", b);
        printf("c = %f\n", c);

        float expected = a * b + c;
        printf("Expected result (a*b+c) = HEX %x\n", *reinterpret_cast<uint32_t*>(&expected));
        printf("Expected result (a*b+c) = %f\n", expected);
    }
}


void display(){
    if(top->io_is_fp32){
        printf("---------DUT result-----------\n");
        printf("DUT result HEX: %x\n", Sim_IO.res_out);
        printf("DUT result :              ");
        fp32_print(&Sim_IO.res_out, 32);
        printf("\n");
    }
    else if(top->io_is_fp16){
        printf("---------new sample-----------\n");
        printf("DUT res_out:\n");
        fp16_print(&Sim_IO.res_out, 32);
    }
    else if(top->io_is_bf16){
        printf("---------new sample-----------\n");
        printf("DUT res_out:\n");
        bf16_print(&Sim_IO.res_out, 32);
        printf("res_out16hex:%x\n", Sim_IO.res_out);
    }
}


void gen_rand_input() {
    top->io_valid_in    = 1;
    top->io_is_bf16     = 0;
    top->io_is_fp16     = 0;
    top->io_is_fp32     = 1;
    top->io_is_widen    = 0;
    
    float a_in = 2.0f;
    float b_in = 5.0f;
    float c_in = -17.0f;

    uint32_t fp_a;     
    memcpy(&fp_a, &a_in, sizeof(float));
    uint32_t fp_b;     
    memcpy(&fp_b, &b_in, sizeof(float));
    uint32_t fp_c;     
    memcpy(&fp_c, &c_in, sizeof(float));

    for(int i = 0; i < 1; i++){
        Sim_IO.a_in = fp_a;
        Sim_IO.b_in = fp_b;
        Sim_IO.c_in = fp_c;
    }
    get_expected_result();

    top->io_a_in = Sim_IO.a_in;
    top->io_b_in = Sim_IO.b_in;
    top->io_c_in = Sim_IO.c_in;
}

void check_result(){
    return ;
}
void get_output() {
    Sim_IO.res_out = top->io_res_out;
}   

void reset(int n) {
    top->reset = 1;
    while (n-- > 0) single_cycle();
    top->reset = 0;
    top->eval();
}

void sim_main(int argc, char *argv[]) {
    int max_cycles = 20;  
    sim_init(argc, argv);
    reset(2);
    gen_rand_input();
    single_cycle();
    /* main loop */
    while (max_cycles--) {
        if(top->io_valid_out){
            single_cycle();
            break;
        }
        else{
            top->io_valid_in = 0;
            single_cycle();
        }
    }
    get_output();
    display();

    sim_exit();
}


//---- Old code of reduction ----

// void gen_rand_vctrl_redu() {
//     Sim_IO.is_bf16       = 0;
//     Sim_IO.is_fp16       = 0;
//     Sim_IO.is_fp32       = 1;
//     Sim_IO.is_widen      = 0;

//     top->io_fire        = 1;
//     top->io_is_bf16     = Sim_IO.is_bf16;
//     top->io_is_fp16     = Sim_IO.is_fp16;
//     top->io_is_fp32     = Sim_IO.is_fp32;
//     top->io_is_widen    = Sim_IO.is_widen;
// }

// void get_expected_result_redu() {

//     int vlmul;
//     switch (Sim_IO.vlmul)
//     {
//     case 3: vlmul = 8; break;
//     case 2: vlmul = 4; break;
//     case 1: vlmul = 2; break;
//     case 0: vlmul = 1; break;
//     default: vlmul = 0; break;
//     }
//     printf("\nvlmul: %d\n", vlmul);
//     if(Sim_IO.fp_format == 2){
//         float acc = (*(float*)&Sim_IO.vs1[0]);  

//         printf("vs1:\n");
//         printf("%12.4f\n", *(float*)&Sim_IO.vs1[0]);
//         printf("vs2:\n");
//         printf("%12.4f\n", *(float*)&Sim_IO.vs2[0]);

//         for (int j = 0; j < vlmul; j++) {
//             for (int i = 0; i < VLEN/XLEN; i++) {
//                 acc = acc + *(float*)&Sim_IO.vs2[0];
//             }
//         }

//         Sim_IO.expected_vd = *(uint32_t*)&acc;
//         printf("expected vd:\n");
//         printf("%12.4f\n", acc);

//     }
//     else if(Sim_IO.fp_format == 1){
//         uint16_t low_fp16 = (uint16_t)(Sim_IO.vs2[0] & 0xFFFF);
//         float acc = half_to_float(low_fp16);  

//         printf("vs1:\n");
//         printf("%12.4f\n", Sim_IO.vs1);
//         printf("vs2:\n");
//         printf("%12.4f\n", acc);

//         uint16_t fp16[VLEN/16];
//         memcpy(fp16, Sim_IO.vs1, VLEN/16 * sizeof(uint16_t));

//         for (int j = 0; j < vlmul; j++) {
//             for (int i = 0; i < VLEN / 16; i++) {
//             half val = fp16[i];

//             float fp16 = half_to_float(val);
//             acc = acc + fp16;
//             }
//         }
//         Sim_IO.expected_vd = *(uint32_t*)&acc;
//         printf("expected vd:\n");
//         printf("%12.4f\n", acc);
//     }
// }

// void gen_rand_input_redu() {
//     float val_a = 1444.0f;               
//     uint32_t fp_a;     
//     memcpy(&fp_a, &val_a, sizeof(float));

//     float val_b = 11.112f;               
//     uint32_t fp_b;     
//     memcpy(&fp_b, &val_b, sizeof(float));
    
//     // 数值 (Decimal)	FP16 二进制	十六进制 (Hex)
//     // 1.0	0 01111 0000000000	0x3C00
//     // 2.0	0 10000 0000000000	0x4000
//     // 3.0	0 10000 1000000000	0x4200
//     // 4.0	0 10001 0000000000	0x4400
//     // 5.0	0 10001 0100000000	0x4560
//     // 6.0	0 10001 1000000000	0x4600
//     // 7.0	0 10001 1100000000	0x46E0
//     // 8.0	0 10010 0000000000	0x4800
//     // 9.0	0 10010 0010000000	0x3C00
//     // 10.0	0 10010 0100000000	0x4900

//     // BF16 (Hex)	对应 float 值	说明
//     // 0x0000	0.0	零
//     // 0x3f80	1.0	常用单位值
//     // 0x4000	2.0	常用整数
//     // 0x4040	3.0	常用整数
//     // 0x4080	4.0	常用整数
//     // 0x3f00	0.5	一半
//     // 0x3e80	0.25	四分之一
//     // 0x3c00	0.0625	十六分之一
//     // 0x7f80	+Infinity	正无穷
//     // 0xff80	-Infinity	负无穷
//     // 0x7fc0	NaN	非数（NaN）
//     // 0xbf80	-1.0	负一
//     // 0xc000	-2.0	负二

//     // uint16_t val_a = 0x7f80;
//     // uint16_t val_b = 0xc000;
//     // uint32_t fp_a = (val_a << 16) | val_a;
//     // uint32_t fp_b = (val_b << 16) | val_b;


//     gen_rand_vctrl();
//     for(int i = 0; i < VLEN/XLEN; i++){
//         Sim_IO.vs1[i] = fp_b;
//         Sim_IO.vs2[i] = fp_a;
//     }
//     get_expected_result();

//     top->io_vs2_0 = Sim_IO.vs2[0];
//     top->io_vs2_1 = Sim_IO.vs2[1];
//     top->io_vs2_2 = Sim_IO.vs2[2];
//     top->io_vs2_3 = Sim_IO.vs2[3];
//     top->io_vs2_4 = Sim_IO.vs2[4];
//     top->io_vs2_5 = Sim_IO.vs2[5];
//     top->io_vs2_6 = Sim_IO.vs2[6];
//     top->io_vs2_7 = Sim_IO.vs2[7];
//     top->io_vs2_8 = Sim_IO.vs2[8];
//     top->io_vs2_9 = Sim_IO.vs2[9];
//     top->io_vs2_10 = Sim_IO.vs2[10];
//     top->io_vs2_11 = Sim_IO.vs2[11];
//     top->io_vs2_12 = Sim_IO.vs2[12];
//     top->io_vs2_13 = Sim_IO.vs2[13];
//     top->io_vs2_14 = Sim_IO.vs2[14];
//     top->io_vs2_15 = Sim_IO.vs2[15];
    
//     top->io_vs1_0 = Sim_IO.vs1[0];
//     top->io_vs1_1 = Sim_IO.vs1[1];
//     top->io_vs1_2 = Sim_IO.vs1[2];
//     top->io_vs1_3 = Sim_IO.vs1[3];
//     top->io_vs1_4 = Sim_IO.vs1[4];
//     top->io_vs1_5 = Sim_IO.vs1[5];
//     top->io_vs1_6 = Sim_IO.vs1[6];
//     top->io_vs1_7 = Sim_IO.vs1[7];
//     top->io_vs1_8 = Sim_IO.vs1[8];
//     top->io_vs1_9 = Sim_IO.vs1[9];
//     top->io_vs1_10 = Sim_IO.vs1[10];
//     top->io_vs1_11 = Sim_IO.vs1[11];
//     top->io_vs1_12 = Sim_IO.vs1[12];
//     top->io_vs1_13 = Sim_IO.vs1[13];
//     top->io_vs1_14 = Sim_IO.vs1[14];
//     top->io_vs1_15 = Sim_IO.vs1[15];

//     top->io_vs2_0 = Sim_IO.vs2[0];
// }