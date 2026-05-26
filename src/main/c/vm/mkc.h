#ifndef MKC_H
#define MKC_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include "bytecode.h"

#define TAG_INTEGER 0x01
#define TAG_STRING 0x02
#define TAG_FUNCTION 0x03

int mkc_read(FILE *f, ByteCode *out);

#endif
