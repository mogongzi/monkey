#ifndef MKC_H
#define MKC_H

#include <stdio.h>
#include <stdint.h>
#include <stddef.h>

#define TAG_INTEGER 0x01

typedef struct
{
  uint8_t tag;
  union
  {
    int64_t integer;
  } as;
} MkcConstant;

typedef struct
{
  uint16_t num_constants;
  MkcConstant *constants;
  uint32_t num_instructions;
  uint8_t *instructions;
} MkcBytecode;

int mkc_read(FILE *f, MkcBytecode *out);
void mkc_free(MkcBytecode *bc);

#endif