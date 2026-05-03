#ifndef MKC_H
#define MKC_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#define TAG_INTEGER 0x01
#define TAG_STRING 0x02

typedef struct {
  uint32_t byte_len;
  char *value;
} MkcString;

typedef struct {
  uint8_t tag;
  union {
    int64_t integer;
    MkcString string;
  } as;
} MkcConstant;

typedef struct {
  uint16_t num_constants;
  MkcConstant *constants;
  uint32_t num_instructions;
  uint8_t *instructions;
} MkcBytecode;

int mkc_read(FILE *f, MkcBytecode *out);
void mkc_free(MkcBytecode *bc);

#endif
