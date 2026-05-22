#ifndef BYTECODE_H
#define BYTECODE_H

#include "object.h"
#include <stdint.h>

typedef struct {
  uint8_t * instructions;
  int num_instructions;
  MObject *constants;
  int num_constants;
} ByteCode;

void free_bytecode(ByteCode *bc);

#endif
