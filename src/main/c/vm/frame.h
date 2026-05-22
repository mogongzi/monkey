#include "object.h"

typedef struct {
    const MCompiledFunction *fn;
    uint32_t ip;
} Frame;

static Frame *new_frame();
