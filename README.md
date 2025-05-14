# Simple-Test-For-Vredusum

This project provides a simple test framework for verifying the functionality of `vfredsum` and `vfredmax` operations.

## Supported Features

- **vfredsum**: Vector reduction sum operation.
- **vfredmax**: Vector reduction maximum operation.

## Commands

* Prepare environment with verilator/mill.
* `make run` to run the test

Others:

* `make clean` to clean build dir.

## Modifying Control

You can modify the `gen_rand_vctrl` function in the `sim.cpp` file. This function sets the control signals for the simulation. Below is a brief explanation of the key parameters you can modify:

