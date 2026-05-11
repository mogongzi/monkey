CC       = cc
CFLAGS   = -Wall -Wextra -std=c11 -g -fsanitize=address -fno-omit-frame-pointer
TEST_DIR = ../../../test/c/vm
BUILD_DIR = ../../../../build

all: test_mkc test_vm dump_mkc

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

test_mkc: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/test_mkc.c mkc.c bytes.c

test_vm: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ $(TEST_DIR)/test_vm.c mkc.c bytes.c vm.c hash_table.c

dump_mkc: $(BUILD_DIR)
	$(CC) $(CFLAGS) -o $(BUILD_DIR)/$@ dump_mkc.c mkc.c bytes.c

clean:
	rm -f $(BUILD_DIR)/test_mkc $(BUILD_DIR)/test_vm $(BUILD_DIR)/dump_mkc

.PHONY: all clean test_mkc test_vm dump_mkc