#ifndef __SIM_H__
#define __SIM_H__

void single_cycle();
void reset(int n);
void sim_init(int argc, char *argv[]);
void sim_exit();

void sim_main(int argc, char *argv[]);


uint64_t rand64() {
  uint64_t tmp = rand();
  tmp = (tmp << 32) + (uint32_t) rand();
  return tmp;
}

#endif