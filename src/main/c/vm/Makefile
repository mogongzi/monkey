CC        = cc
CFLAGS    = -Wall -Wextra -std=c11 -g -fsanitize=address -fno-omit-frame-pointer
BENCH_CFLAGS = -Wall -Wextra -std=c11 -O2
TEST_DIR  = ../../../test/c/vm
BUILD_DIR = ../../../../build

# Source groupings — every target needs the loader; only the VM needs the runtime.
LOADER_SRCS  = mkc.c bytecode.c object.c bytes.c
RUNTIME_SRCS = vm.c hash_table.c frame.c builtins.c arena.c

all: test_mkc test_vm dump_mkc

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

test_mkc: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/test_mkc.c $(LOADER_SRCS)

test_vm: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/test_vm.c $(LOADER_SRCS) $(RUNTIME_SRCS)

dump_mkc: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ dump_mkc.c $(LOADER_SRCS)

benchmark:
	mkdir -p $(BUILD_DIR)
	$(CC) $(BENCH_CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/benchmark.c $(LOADER_SRCS) $(RUNTIME_SRCS)

clean:
	rm -f $(BUILD_DIR)/test_mkc $(BUILD_DIR)/test_vm $(BUILD_DIR)/dump_mkc $(BUILD_DIR)/benchmark

.PHONY: all clean test_mkc test_vm dump_mkc