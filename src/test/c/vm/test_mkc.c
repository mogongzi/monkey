#include "../../../main/c/vm/mkc.h"
#include "../../../main/c/vm/opcodes.h"

#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static FILE *bytes_to_stream(const uint8_t *data, size_t len)
{
  static const char *path = "/tmp/test.mkc";
  FILE *f = fopen(path, "wb");
  if (!f)
  {
    perror(path);
    abort();
  }
  fwrite(data, 1, len, f);
  fclose(f);
  FILE *rf = fopen(path, "rb");
  if (!rf)
  {
    perror(path);
    abort();
  }
  return rf;
}

// test1: empty program
static void test_empty_program(void)
{
  uint8_t data[] = {
      0x00, 0x00,
      0x00, 0x00, 0x00, 0x00};

  FILE *f = bytes_to_stream(data, sizeof(data));
  ByteCode bc;
  assert(mkc_read(f, &bc) == 0);
  fclose(f);
  assert(bc.num_constants == 0);
  assert(bc.constants == NULL);
  assert(bc.num_constants == 0);
  assert(bc.instructions == NULL);
  free_bytecode(&bc);
  printf("  PASS test_empty_program\n");
}

// test2: one integer constant
static void test_one_integer(void)
{
  uint8_t data[] = {
      0x00, 0x01,
      TAG_INTEGER,                                    // constant - tag
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2A, // constant[0]: 42
      0x00, 0x00, 0x00, 0x00};
  FILE *f = bytes_to_stream(data, sizeof(data));
  ByteCode bc;
  assert(mkc_read(f, &bc) == 0);
  fclose(f);
  assert(bc.num_constants == 1);
  assert(bc.constants[0].type == MINTEGER);
  assert(bc.constants[0].as.integer == 42);
  assert(bc.num_instructions == 0);
  free_bytecode(&bc);
  printf("  PASS test_one_integer\n");
}

// test3: negative integer
static void test_negative_integer(void)
{
  uint8_t data[] = {
      0x00, 0x01,
      TAG_INTEGER,
      0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xD6, // -42 in two's complement
      0x00, 0x00, 0x00, 0x00};

  FILE *f = bytes_to_stream(data, sizeof(data));
  ByteCode bc;
  assert(mkc_read(f, &bc) == 0);
  fclose(f);
  assert(bc.constants[0].as.integer == -42);
  free_bytecode(&bc);
  printf("  PASS test_negative_integer\n");
}

// test4: 2 constants with instructions - ("1 + 2")
static void test_two_constants_with_instructions(void)
{
  uint8_t data[] = {
      0x00, 0x02,
      TAG_INTEGER,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
      TAG_INTEGER,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02,
      0x00, 0x00, 0x00, 0x06, // 6 instructions bytes
      0x00, 0x00, 0x00,
      0x00, 0x00, 0x01};

  // verify size matches
  assert(sizeof(data) == 30);

  FILE *f = bytes_to_stream(data, sizeof(data));
  ByteCode bc;
  assert(mkc_read(f, &bc) == 0);
  fclose(f);
  // constants
  assert(bc.num_constants == 2);
  assert(bc.constants[0].as.integer == 1);
  assert(bc.constants[1].as.integer == 2);

  // instructions
  assert(bc.num_instructions == 6);
  assert(bc.instructions[0] == 0x00);
  assert(bc.instructions[1] == 0x00);
  assert(bc.instructions[2] == 0x00);
  assert(bc.instructions[3] == 0x00);
  assert(bc.instructions[4] == 0x00);
  assert(bc.instructions[5] == 0x01);

  free_bytecode(&bc);
  printf("  PASS test_two_constants_with_instructions\n");
}

// test5 : truncated file
static void test_truncated_constants(void)
{
  uint8_t data[] = {
      0x00, 0x01,
      TAG_INTEGER,
      0x00, 0x00 // only 2 of 8 payload bytes
  };

  FILE *f = bytes_to_stream(data, sizeof(data));
  ByteCode bc;
  assert(mkc_read(f, &bc) != 0);
  fclose(f);
  printf("  PASS test_truncated_constants\n");
}

// test6: reject trailing bytes
static void test_trailing_bytes(void)
{
  uint8_t data[] = {
      0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,
      0xFF};

  FILE *f = bytes_to_stream(data, sizeof(data));
  ByteCode bc;
  assert(mkc_read(f, &bc) != 0);
  fclose(f);
  printf("  PASS test_trailing_bytes\n");
}

// test9: test function call
static void test_function_call(void) {
    uint8_t data[] = {
        0x00, 0x01,              // 1 constant
        TAG_FUNCTION,            // constant tag
        0x00, 0x00, 0x00, 0x01,  // function instruction length = 1
        OP_RETURN,               // function instructions
        0x00, 0x00, 0x00, 0x00   // main instruction length = 0
    };

    // verify size matches
    assert(sizeof(data) == 12);

    FILE *f = bytes_to_stream(data, sizeof(data));
    ByteCode bc;
    assert(mkc_read(f, &bc) == 0);
    fclose(f);
    assert(bc.num_constants == 1);
    assert(bc.constants[0].type == MCOMPILED_FUNCTION);
    assert(bc.constants[0].as.function->num_instructions == 1);
    assert(bc.constants[0].as.function->instructions[0] == OP_RETURN);

    assert(bc.num_instructions == 0);
    assert(bc.instructions == NULL);

    free_bytecode(&bc);
    printf("  PASS test_function_call\n");
}

int main(void)
{
  printf("Running mkc tests...\n");
  test_empty_program();
  test_one_integer();
  test_negative_integer();
  test_two_constants_with_instructions();
  test_truncated_constants();
  test_trailing_bytes();
  test_function_call();
  printf("All tests passed.\n");
  return 0;
}
