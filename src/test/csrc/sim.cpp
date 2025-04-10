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
    tfp->open("build/top.vcd");
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
        printf("%12.4f ", fsrc[j]);
        if ((j + 1) % 4 == 0) {                     // 每行输出4个数
            printf("\n");
        }
    }
}


void display(){
    if(top->io_fp_format == 2){
        printf("---------new sample-----------\n");
        // printf("vs1:\n");
        // fp32_print(Sim_IO.vs1, VLEN);
        // printf("vs2:\n");
        // fp32_print(Sim_IO.vs2, VLEN);
        printf("vd:\n");
        fp32_print(&Sim_IO.vd, XLEN);
        printf("\n");
        // printf("expected vd:\n");
        // printf("%12.4f\n", Sim_IO.expected_vd);
    }
    else if(top->io_fp_format == 1){
        printf("---------new sample-----------\n");
        // printf("vs1:\n");
        // fp16_print(Sim_IO.vs1, VLEN);
        // printf("vs2:\n");
        // fp16_print(Sim_IO.vs2, VLEN);
        printf("vd:\n");
        fp16_print(&Sim_IO.vd, 32);
        // printf("\nexpected vd:\n");
        // printf("%20.4f\n", Sim_IO.expected_vd);
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


void index_acum() {

    Sim_IO.index += 1;
    top->io_index = Sim_IO.index;

}
void gen_rand_vctrl() {

    Sim_IO.is_vfredsum = 1;
    Sim_IO.is_vfredmax = 0;
    Sim_IO.vlmul = 3;
    Sim_IO.round_mode = 0;
    Sim_IO.fp_format = 2;
    Sim_IO.is_vec = 1;
    Sim_IO.index = 0;

    top->io_fire = 1;
    top->io_is_vfredsum = Sim_IO.is_vfredsum;
    top->io_is_vfredmax = Sim_IO.is_vfredmax;
    top->io_vlmul = Sim_IO.vlmul;
    top->io_round_mode = Sim_IO.round_mode;
    top->io_fp_format = Sim_IO.fp_format;
    top->io_is_vec = Sim_IO.is_vec;
    top->io_index = Sim_IO.index;

}

void get_expected_result() {

    int vlmul;
    switch (Sim_IO.vlmul)
    {
    case 3: vlmul = 8; break;
    case 2: vlmul = 4; break;
    case 1: vlmul = 2; break;
    case 0: vlmul = 1; break;
    default: vlmul = 0; break;
    }
    printf("vlmul: %d\n", vlmul);
    if(Sim_IO.fp_format == 2){
        float acc = *(float*)&Sim_IO.vs2[0];  

        printf("vs1:\n");
        fp32_print(Sim_IO.vs1, VLEN);
        printf("vs2:\n");
        printf("%12.4f\n", *(float*)&Sim_IO.vs2[0]);

        for (int j = 0; j < vlmul; j++) {
            for (int i = 0; i < VLEN / 32; i++) {
                acc += *(float*)&Sim_IO.vs1[i];
            }
        }
        Sim_IO.expected_vd = *(uint32_t*)&acc;
        printf("expected vd:\n");
        printf("%12.4f\n", acc);

    }
    else if(Sim_IO.fp_format == 1){
        uint16_t low_fp16 = (uint16_t)(Sim_IO.vs2[0] & 0xFFFF);
        float acc = half_to_float(low_fp16);  

        printf("vs1:\n");
        fp16_print(Sim_IO.vs1, VLEN);
        printf("vs2:\n");
        printf("%12.4f\n", acc);

        uint16_t fp16[VLEN/16];
        memcpy(fp16, Sim_IO.vs1, VLEN/16 * sizeof(uint16_t));

        for (int j = 0; j < vlmul; j++) {
            for (int i = 0; i < VLEN / 16; i++) {
            half val = fp16[i];

            float fp16 = half_to_float(val);
            acc = acc + fp16;
            }
        }
        Sim_IO.expected_vd = *(uint32_t*)&acc;
        printf("expected vd:\n");
        printf("%12.4f\n", acc);
    }

}


void gen_rand_input() {
    float val_a = 12.0f;               
    uint32_t fp_a;     
    memcpy(&fp_a, &val_a, sizeof(float));

    float val_b = 11.1f;               
    uint32_t fp_b;     
    memcpy(&fp_b, &val_b, sizeof(float));
    
    // 数值 (Decimal)	FP16 二进制	十六进制 (Hex)
    // 1.0	0 01111 0000000000	0x3C00
    // 2.0	0 10000 0000000000	0x4000
    // 3.0	0 10000 1000000000	0x4200
    // 4.0	0 10001 0000000000	0x4400
    // 5.0	0 10001 0100000000	0x4560
    // 6.0	0 10001 1000000000	0x4600
    // 7.0	0 10001 1100000000	0x46E0
    // 8.0	0 10010 0000000000	0x4800
    // 9.0	0 10010 0010000000	0x48A0
    // 10.0	0 10010 0100000000	0x4900

    // uint16_t val_a = 0x48A0;
    // uint16_t val_b = 0x4000;
    // uint32_t fp_a = (val_a << 16) | val_a;
    // uint32_t fp_b = (val_b << 16) | val_b;

    gen_rand_vctrl();
    for(int i = 0; i < VLEN/XLEN; i++){
        Sim_IO.vs1[i] = fp_a;
        Sim_IO.vs2[i] = fp_b;
    }
    get_expected_result();

    top->io_vs2_0 = Sim_IO.vs2[0];
    top->io_vs2_1 = Sim_IO.vs2[1];
    top->io_vs2_2 = Sim_IO.vs2[2];
    top->io_vs2_3 = Sim_IO.vs2[3];
    top->io_vs2_4 = Sim_IO.vs2[4];
    top->io_vs2_5 = Sim_IO.vs2[5];
    top->io_vs2_6 = Sim_IO.vs2[6];
    top->io_vs2_7 = Sim_IO.vs2[7];
    top->io_vs2_8 = Sim_IO.vs2[8];
    top->io_vs2_9 = Sim_IO.vs2[9];
    top->io_vs2_10 = Sim_IO.vs2[10];
    top->io_vs2_11 = Sim_IO.vs2[11];
    top->io_vs2_12 = Sim_IO.vs2[12];
    top->io_vs2_13 = Sim_IO.vs2[13];
    top->io_vs2_14 = Sim_IO.vs2[14];
    top->io_vs2_15 = Sim_IO.vs2[15];
    // top->io_vs2_16 = Sim_IO.vs2[16];
    // top->io_vs2_17 = Sim_IO.vs2[17];
    // top->io_vs2_18 = Sim_IO.vs2[18];
    // top->io_vs2_19 = Sim_IO.vs2[19];
    // top->io_vs2_20 = Sim_IO.vs2[20];
    // top->io_vs2_21 = Sim_IO.vs2[21];
    // top->io_vs2_22 = Sim_IO.vs2[22];
    // top->io_vs2_23 = Sim_IO.vs2[23];
    // top->io_vs2_24 = Sim_IO.vs2[24];
    // top->io_vs2_25 = Sim_IO.vs2[25];
    // top->io_vs2_26 = Sim_IO.vs2[26];
    // top->io_vs2_27 = Sim_IO.vs2[27];
    // top->io_vs2_28 = Sim_IO.vs2[28];
    // top->io_vs2_29 = Sim_IO.vs2[29];
    // top->io_vs2_30 = Sim_IO.vs2[30];
    // top->io_vs2_31 = Sim_IO.vs2[31];
    // top->io_vs2_32 = Sim_IO.vs2[32];
    // top->io_vs2_33 = Sim_IO.vs2[33];
    // top->io_vs2_34 = Sim_IO.vs2[34];
    // top->io_vs2_35 = Sim_IO.vs2[35];
    // top->io_vs2_36 = Sim_IO.vs2[36];
    // top->io_vs2_37 = Sim_IO.vs2[37];
    // top->io_vs2_38 = Sim_IO.vs2[38];
    // top->io_vs2_39 = Sim_IO.vs2[39];
    // top->io_vs2_40 = Sim_IO.vs2[40];
    // top->io_vs2_41 = Sim_IO.vs2[41];
    // top->io_vs2_42 = Sim_IO.vs2[42];
    // top->io_vs2_43 = Sim_IO.vs2[43];
    // top->io_vs2_44 = Sim_IO.vs2[44];
    // top->io_vs2_45 = Sim_IO.vs2[45];
    // top->io_vs2_46 = Sim_IO.vs2[46];
    // top->io_vs2_47 = Sim_IO.vs2[47];
    // top->io_vs2_48 = Sim_IO.vs2[48];
    // top->io_vs2_49 = Sim_IO.vs2[49];
    // top->io_vs2_50 = Sim_IO.vs2[50];
    // top->io_vs2_51 = Sim_IO.vs2[51];
    // top->io_vs2_52 = Sim_IO.vs2[52];
    // top->io_vs2_53 = Sim_IO.vs2[53];
    // top->io_vs2_54 = Sim_IO.vs2[54];
    // top->io_vs2_55 = Sim_IO.vs2[55];
    // top->io_vs2_56 = Sim_IO.vs2[56];
    // top->io_vs2_57 = Sim_IO.vs2[57];
    // top->io_vs2_58 = Sim_IO.vs2[58];
    // top->io_vs2_59 = Sim_IO.vs2[59];
    // top->io_vs2_60 = Sim_IO.vs2[60];
    // top->io_vs2_61 = Sim_IO.vs2[61];
    // top->io_vs2_62 = Sim_IO.vs2[62];
    // top->io_vs2_63 = Sim_IO.vs2[63];
    top->io_vs1_0 = Sim_IO.vs1[0];
    top->io_vs1_1 = Sim_IO.vs1[1];
    top->io_vs1_2 = Sim_IO.vs1[2];
    top->io_vs1_3 = Sim_IO.vs1[3];
    top->io_vs1_4 = Sim_IO.vs1[4];
    top->io_vs1_5 = Sim_IO.vs1[5];
    top->io_vs1_6 = Sim_IO.vs1[6];
    top->io_vs1_7 = Sim_IO.vs1[7];
    top->io_vs1_8 = Sim_IO.vs1[8];
    top->io_vs1_9 = Sim_IO.vs1[9];
    top->io_vs1_10 = Sim_IO.vs1[10];
    top->io_vs1_11 = Sim_IO.vs1[11];
    top->io_vs1_12 = Sim_IO.vs1[12];
    top->io_vs1_13 = Sim_IO.vs1[13];
    top->io_vs1_14 = Sim_IO.vs1[14];
    top->io_vs1_15 = Sim_IO.vs1[15];
    // top->io_vs1_16 = Sim_IO.vs1[16];
    // top->io_vs1_17 = Sim_IO.vs1[17];
    // top->io_vs1_18 = Sim_IO.vs1[18];
    // top->io_vs1_19 = Sim_IO.vs1[19];
    // top->io_vs1_20 = Sim_IO.vs1[20];
    // top->io_vs1_21 = Sim_IO.vs1[21];
    // top->io_vs1_22 = Sim_IO.vs1[22];
    // top->io_vs1_23 = Sim_IO.vs1[23];
    // top->io_vs1_24 = Sim_IO.vs1[24];
    // top->io_vs1_25 = Sim_IO.vs1[25];
    // top->io_vs1_26 = Sim_IO.vs1[26];
    // top->io_vs1_27 = Sim_IO.vs1[27];
    // top->io_vs1_28 = Sim_IO.vs1[28];
    // top->io_vs1_29 = Sim_IO.vs1[29];
    // top->io_vs1_30 = Sim_IO.vs1[30];
    // top->io_vs1_31 = Sim_IO.vs1[31];
    // top->io_vs1_32 = Sim_IO.vs1[32];
    // top->io_vs1_33 = Sim_IO.vs1[33];
    // top->io_vs1_34 = Sim_IO.vs1[34];
    // top->io_vs1_35 = Sim_IO.vs1[35];
    // top->io_vs1_36 = Sim_IO.vs1[36];
    // top->io_vs1_37 = Sim_IO.vs1[37];
    // top->io_vs1_38 = Sim_IO.vs1[38];
    // top->io_vs1_39 = Sim_IO.vs1[39];
    // top->io_vs1_40 = Sim_IO.vs1[40];
    // top->io_vs1_41 = Sim_IO.vs1[41];
    // top->io_vs1_42 = Sim_IO.vs1[42];
    // top->io_vs1_43 = Sim_IO.vs1[43];
    // top->io_vs1_44 = Sim_IO.vs1[44];
    // top->io_vs1_45 = Sim_IO.vs1[45];
    // top->io_vs1_46 = Sim_IO.vs1[46];
    // top->io_vs1_47 = Sim_IO.vs1[47];
    // top->io_vs1_48 = Sim_IO.vs1[48];
    // top->io_vs1_49 = Sim_IO.vs1[49];
    // top->io_vs1_50 = Sim_IO.vs1[50];
    // top->io_vs1_51 = Sim_IO.vs1[51];
    // top->io_vs1_52 = Sim_IO.vs1[52];
    // top->io_vs1_53 = Sim_IO.vs1[53];
    // top->io_vs1_54 = Sim_IO.vs1[54];
    // top->io_vs1_55 = Sim_IO.vs1[55];
    // top->io_vs1_56 = Sim_IO.vs1[56];
    // top->io_vs1_57 = Sim_IO.vs1[57];
    // top->io_vs1_58 = Sim_IO.vs1[58];
    // top->io_vs1_59 = Sim_IO.vs1[59];
    // top->io_vs1_60 = Sim_IO.vs1[60];
    // top->io_vs1_61 = Sim_IO.vs1[61];
    // top->io_vs1_62 = Sim_IO.vs1[62];
    // top->io_vs1_63 = Sim_IO.vs1[63];

    top->io_vs2_0 = Sim_IO.vs2[0];
}

void check_result(){
    return ;
}
void get_output() {
    // TODO:
    Sim_IO.vd = top->io_vd;
}   

void reset(int n) {
    top->reset = 1;
    while (n-- > 0) single_cycle();
    top->reset = 0;
    top->eval();
}

void sim_main(int argc, char *argv[]) {
    int max_cycles = 100;  
    sim_init(argc, argv);
    reset(10);
    gen_rand_input();
    single_cycle();
    /* main loop */
    while (max_cycles--) {
        if(top->io_finish){
            break;
        }
        else{
            index_acum();
            single_cycle();
        }
    }
    get_output();
    display();

    sim_exit();
}