CC       = cc
CFLAGS   = -Wall -Wextra -std=c11 -g -fsanitize=address -fno-omit-frame-pointer
TEST_DIR = ../../../test/c/vm

all: test_mkc test_vm

test_mkc: $(TEST_DIR)/test_mkc.c mkc.c mkc.h bytes.c bytes.h
	$(CC) $(CFLAGS) -o $@ $(TEST_DIR)/test_mkc.c mkc.c bytes.c

test_vm: $(TEST_DIR)/test_vm.c mkc.c mkc.h bytes.c bytes.h vm.c vm.h
	$(CC) $(CFLAGS) -o $@ $(TEST_DIR)/test_vm.c mkc.c bytes.c vm.c

dump_mkc: dump_mkc.o mkc.o bytes.o
	$(CC) $(CFLAGS) -o $@ $^

clean:
	rm -f *.o test_mkc test_vm dump_mkc

.PHONY: all clean