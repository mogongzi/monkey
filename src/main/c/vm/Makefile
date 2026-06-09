CC        = cc
CFLAGS    = -Wall -Wextra -std=c11 -g -fsanitize=address -fno-omit-frame-pointer
TEST_DIR  = ../../../test/c/vm
BUILD_DIR = ../../../../build

# Source groupings — every target needs the loader; only the VM needs the runtime.
LOADER_SRCS  = mkc.c bytecode.c object.c bytes.c
RUNTIME_SRCS = vm.c hash_table.c frame.c builtins.c

all: test_mkc test_vm dump_mkc

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

test_mkc: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/test_mkc.c $(LOADER_SRCS)

test_vm: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/test_vm.c $(LOADER_SRCS) $(RUNTIME_SRCS)

dump_mkc: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ dump_mkc.c $(LOADER_SRCS)

clean:
	rm -f $(BUILD_DIR)/test_mkc $(BUILD_DIR)/test_vm $(BUILD_DIR)/dump_mkc

.PHONY: all clean test_mkc test_vm dump_mkc