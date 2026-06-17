#ifndef FRAME_H
#define FRAME_H

#include "bytecode.h"
#include "object.h"

typedef struct {
  const MClosure *closure;
  uint32_t ip;
  uint32_t bp;  // base pointer
} Frame;

#endif
