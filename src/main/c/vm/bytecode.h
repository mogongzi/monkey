#ifndef BYTECODE_H
#define BYTECODE_H

#include "object.h"
#include <stdint.h>

typedef struct {
  uint8_t * instructions;
  uint32_t num_instructions;
  MObject *constants;
  uint32_t num_constants;
} ByteCode;

void free_bytecode(ByteCode *bc);

#endif
