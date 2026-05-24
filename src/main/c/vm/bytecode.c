#include "bytecode.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void free_bytecode(ByteCode *bc) {
  if (!bc)
    return;
  if (bc->constants) {
    for (uint32_t i = 0; i < bc->num_constants; i++) {
      switch (bc->constants[i].type) {
      case MSTRING:
        free(bc->constants[i].as.string);
        break;
      case MCOMPILED_FUNCTION:
        free((void *)bc->constants[i].as.function->instructions);
        free(bc->constants[i].as.function);
        break;
      default:
        break; /* MINTEGER: no heap data */
      }
    }
    free(bc->constants);
  }
  free(bc->instructions);
  memset(bc, 0, sizeof(*bc));
}
