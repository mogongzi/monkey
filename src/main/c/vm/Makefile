CC       = cc
CFLAGS   = -Wall -Wextra -std=c11 -g
TEST_DIR = ../../../test/c/vm

all: test_mkc

test_mkc: $(TEST_DIR)/test_mkc.c mkc.c mkc.h
	$(CC) $(CFLAGS) -o $@ $(TEST_DIR)/test_mkc.c mkc.c

dump_mkc: dump_mkc.o mkc.o
	$(CC) $(CFLAGS) -o $@ $^

clean:
	rm -f *.o test_mkc dump_mkc

.PHONY: all clean