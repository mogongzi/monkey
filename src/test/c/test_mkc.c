#include "../../main/c/mkc.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

static FILE *bytes_to_stream(const uint8_t *data, size_t len) {
  static const char *path = "/temp/test.mkc";
  FILE *f = fopen(path, "wb");
  fwrite(data, 1, len, f);
  fclose(f);
  return fopen(path, "rb");
}

static void test_empty_program(void) {
  uint8_t data[] = {
    0x00, 0x00,
    0x00, 0x00, 0x00, 0x00
  };

  FILE *f = bytes_to_stream(data, sizeof(data));
  MkcBytecode bc;
  assert(mkc_read(f, &bc) == 0);
  assert(bc.num_constants == 0);
  assert(bc.constants == NULL);
  assert(bc.num_instructions == 0);
  assert(bc.instructions == NULL);
  mkc_free(&bc);
  printf ("  PASS test_empty_program\n");
}

int main(void) {
    printf("Running mkc tests...\n");
    test_empty_program();
    return 0;
}
