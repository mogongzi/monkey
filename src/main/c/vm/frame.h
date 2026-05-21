#include "mkc.h"

typedef struct {
    const MkcFunction *fn;
    uint32_t ip;
} Frame;

static Frame *new_frame()
