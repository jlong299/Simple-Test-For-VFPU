TOP = top
MAIN = top.topMain
BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/OBJ_DIR
TOPNAME = top
TOP_V = $(BUILD_DIR)/$(TOPNAME).v

SCALA_FILE = $(shell find ./src/main/ -name '*.scala')

VERILATOR = verilator
VERILATOR_COVERAGE = verilator_coverage
# verilator flags
VERILATOR_FLAGS +=  -MMD --trace --build -cc --exe \
	                                 -O3 --x-assign fast --x-initial fast --noassert -report-unoptflat

# timescale set
VERILATOR_FLAGS += --timescale 1us/1us
VERILATOR_FLAGS += -j 33

$(TOP_V): $(SCALA_FILE)
	@mkdir -p $(@D)
	mill $(TOP).runMain $(MAIN) -td $(@D) --output-file $(@F)

verilog: $(TOP_V)

vcd ?= 1
ifeq ($(vcd), 1)
    CFLAGS += -DVCD
endif

# C flags
INC_PATH += $(abspath ./src/test/csrc/include)
INCFLAGS = $(addprefix -I, $(INC_PATH))
CFLAGS += $(INCFLAGS) $(CFLAGS_SIM) -DTOP_NAME="V$(TOPNAME)"


# source file
VSRCS = $(TOP_V)
CSRCS = $(shell find $(abspath ./src/test/csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")

BIN = $(BUILD_DIR)/$(TOP)
NPC_EXEC := $(BIN)


compile: $(VSRCS) $(CSRCS)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_FLAGS) -top $(TOPNAME) $(VSRCS) $(CSRCS) \
	$(addprefix -CFLAGS , $(CFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
	--Mdir $(OBJ_DIR) -o $(abspath $(BIN))

run: $(BIN)
	@echo
	@echo "------------ RUN --------------"
	$(NPC_EXEC)
	@echo "----- if you need vcd file. add vcd=y to make ----"

srun: compile run

clean:
	rm -rf $(BUILD_DIR)

clean_mill:
	rm -rf out

clean_all: clean clean_mill

.PHONY: clean clean_all clean_mill srun run sim verilog