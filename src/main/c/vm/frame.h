#ifndef FRAME_H
#define FRAME_H

#include "bytecode.h"
#include "object.h"

typedef struct {
    const MCompiledFunction *fn;
    uint32_t ip;
} Frame;

#endif
